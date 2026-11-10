package com.upupprogress.app.data.repository

import com.upupprogress.app.data.model.*
import com.upupprogress.app.data.remote.SupabaseClientProvider
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.*

interface FoodDiaryGateway {
    fun currentUserId(): String?
    suspend fun loadDay(date: LocalDate): FoodDay
    suspend fun loadRecordedDates(start: LocalDate, end: LocalDate): Set<LocalDate>
    suspend fun lookup(barcode: String): FoodProduct
    suspend fun record(request: FoodEntryRequest): FoodEntry
}

private const val ENTRY_COLUMNS = "id,user_id,product_id,consumed_on,meal_type,quantity,unit,product_name_snapshot," +
    "energy_kcal_100_snapshot,protein_g_100_snapshot,carbs_g_100_snapshot,fat_g_100_snapshot"
private const val PRODUCT_COLUMNS = "id,barcode,name,brand,nutrition_basis,energy_kcal_100,protein_g_100,carbs_g_100,fat_g_100"

class FoodDiaryRepository internal constructor(
    private val client: SupabaseClient,
    private val userId: () -> String?,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) : FoodDiaryGateway {
    constructor() : this(SupabaseClientProvider.client, { SupabaseClientProvider.client.auth.currentUserOrNull()?.id })
    override fun currentUserId(): String? = userId()

    private fun actor(expected: String? = null): String {
        val id = checkNotNull(currentUserId()) { "Inicia sesión para continuar." }
        check(expected == null || expected == id) { "La sesión cambió." }
        return id
    }

    private suspend fun ensureActor(id: String) {
        currentCoroutineContext().ensureActive()
        actor(id)
    }

    override suspend fun lookup(barcode: String): FoodProduct {
        require(validFoodBarcode(barcode))
        val userId = actor()
        val response = client.functions.invoke("lookup-food-product") {
            method = HttpMethod.Get
            parameter("barcode", barcode)
        }
        ensureActor(userId)
        return FoodProduct.fromJson(Json.parseToJsonElement(response.bodyAsText()).jsonObject, barcode)
    }

    override suspend fun loadRecordedDates(start: LocalDate, end: LocalDate): Set<LocalDate> {
        require(end >= start && end <= start.plusDays(6))
        val userId = actor()
        val dates = mutableSetOf<LocalDate>()
        var offset = 0L
        do {
            val page = client.from("food_entries").select(Columns.raw("consumed_on")) {
                filter {
                    eq("user_id", userId)
                    and {
                        gte("consumed_on", start.toString())
                        lte("consumed_on", end.toString())
                    }
                }
                order("consumed_on", Order.ASCENDING)
                order("id", Order.ASCENDING)
                range(offset, offset + 499)
            }.decodeList<JsonObject>()
            ensureActor(userId)
            page.forEach {
                val date = LocalDate.parse(requireNotNull(it.string("consumed_on")))
                check(date in start..end)
                dates.add(date)
            }
            offset += 500
        } while (page.size == 500)
        return dates
    }

    override suspend fun loadDay(date: LocalDate): FoodDay {
        val userId = actor()
        val entries = mutableListOf<FoodEntry>()
        var offset = 0L
        do {
            val page = client.from("food_entries").select(Columns.raw(ENTRY_COLUMNS)) {
                filter { eq("user_id", userId); eq("consumed_on", date.toString()) }
                order("created_at", Order.ASCENDING)
                order("id", Order.ASCENDING)
                range(offset, offset + 499)
            }.decodeList<JsonObject>().map(FoodEntry::fromJson)
            ensureActor(userId)
            check(page.all { it.userId == userId && it.date == date })
            entries.addAll(page)
            offset += 500
        } while (page.size == 500)

        val goal = client.from("user_goals").select(Columns.raw(
            "daily_calorie_target,daily_protein_target_g,daily_carbs_target_g,daily_fat_target_g"
        )) {
            filter {
                eq("user_id", userId)
                lt("effective_at", foodDayEndExclusive(date, zone()))
            }
            order("effective_at", Order.DESCENDING)
            order("id", Order.DESCENDING)
            limit(1)
        }.decodeList<JsonObject>().firstOrNull()
        ensureActor(userId)
        val calories = goal?.string("daily_calorie_target")?.toIntOrNull()
        val protein = goal?.decimal("daily_protein_target_g")
        val carbs = goal?.decimal("daily_carbs_target_g")
        val fat = goal?.decimal("daily_fat_target_g")
        val targets = if (calories != null && protein != null && carbs != null && fat != null)
            NutritionTargets(calories, protein, carbs, fat) else null
        return FoodDay(date, entries.distinctBy { it.id }, targets)
    }

    override suspend fun record(request: FoodEntryRequest): FoodEntry {
        actor(request.userId)
        findEntry(request)?.let { return verifyEntry(it, request) }
        val product = catalogProduct(request.barcode, request.userId)
        require(product.canRecord && product.basis?.unit == request.unit) { "La información o la unidad del producto cambió. Vuelve a escanearlo." }
        ensureActor(request.userId)
        val saved = try {
            client.from("food_entries").insert(request.toInsert(product)) { select(Columns.raw(ENTRY_COLUMNS)) }
                .decodeSingle<JsonObject>().let(FoodEntry::fromJson)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            // Una respuesta perdida no debe repetir el consumo con otro ID.
            val recovered = try { findEntry(request) } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) { null }
            recovered ?: throw error
        }
        ensureActor(request.userId)
        return verifyEntry(saved, request)
    }

    private suspend fun findEntry(request: FoodEntryRequest): FoodEntry? {
        ensureActor(request.userId)
        val row = client.from("food_entries").select(Columns.raw(ENTRY_COLUMNS)) {
            filter { eq("user_id", request.userId); eq("id", request.id) }
            limit(1)
        }.decodeList<JsonObject>().firstOrNull()
        ensureActor(request.userId)
        return row?.let(FoodEntry::fromJson)
    }

    private fun verifyEntry(entry: FoodEntry, request: FoodEntryRequest): FoodEntry {
        check(entry.id == request.id && entry.userId == request.userId && entry.date == request.date &&
            entry.meal == request.meal && entry.unit == request.unit && entry.quantity.compareTo(request.quantity) == 0)
        return entry
    }

    private suspend fun catalogProduct(barcode: String, userId: String): FoodProduct {
        ensureActor(userId)
        try {
            val response = client.functions.invoke(
                function = "sync-food-product",
                body = buildJsonObject { put("barcode", barcode) },
                headers = Headers.build { append(HttpHeaders.ContentType, "application/json") },
            )
            ensureActor(userId)
            val productId = requireNotNull(Json.parseToJsonElement(response.bodyAsText()).jsonObject.string("product_id"))
            val row = client.from("food_products").select(Columns.raw(PRODUCT_COLUMNS)) {
                filter { eq("id", productId) }
                limit(1)
            }.decodeSingle<JsonObject>()
            ensureActor(userId)
            return FoodProduct.fromJson(row, barcode)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            // Otro dispositivo pudo sincronizarlo antes, o pudo perderse la respuesta.
            ensureActor(userId)
            val visible = client.from("food_products").select(Columns.raw(PRODUCT_COLUMNS)) {
                filter { eq("barcode", barcode) }
                limit(1)
            }.decodeList<JsonObject>().firstOrNull()
            ensureActor(userId)
            return visible?.let { FoodProduct.fromJson(it, barcode) } ?: throw error
        }
    }
}
