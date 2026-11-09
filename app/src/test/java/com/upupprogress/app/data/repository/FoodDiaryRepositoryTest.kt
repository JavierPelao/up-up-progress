package com.upupprogress.app.data.repository

import com.upupprogress.app.data.model.*
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.logging.LogLevel
import io.github.jan.supabase.postgrest.Postgrest
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/** Contratos HTTP del SDK real contra un motor en memoria: nunca conecta a Supabase. */
@RunWith(JUnit4::class)
class FoodDiaryRepositoryTest {
    private val user = "00000000-0000-0000-0000-000000000001"
    private val productId = "00000000-0000-0000-0000-000000000002"
    private val entryId = "00000000-0000-0000-0000-000000000003"
    private val date = LocalDate.of(2026, 11, 7)
    private val barcode = "0012345678905"
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    private val product = """{"id":"$productId","barcode":"$barcode","name":"Ejemplo","nutrition_basis":"100g","energy_kcal_100":128,"protein_g_100":6.4,"carbs_g_100":21.3,"fat_g_100":1.5}"""

    private fun entry(id: String = entryId, quantity: String = "50") =
        """{"id":"$id","user_id":"$user","product_id":"$productId","consumed_on":"$date","meal_type":"lunch","quantity":$quantity,"unit":"g","product_name_snapshot":"Ejemplo","energy_kcal_100_snapshot":128,"protein_g_100_snapshot":6.4,"carbs_g_100_snapshot":21.3,"fat_g_100_snapshot":1.5}"""

    private fun client(engine: MockEngine) = createSupabaseClient("https://example.invalid", "synthetic-key") {
        httpEngine = engine
        defaultLogLevel = LogLevel.NONE
        osInformation = null
        install(Functions) { requireValidSession = false }
        install(Postgrest) { requireValidSession = false }
    }

    @Test fun lookupUsesGetAndPreservesLeadingZerosInTheQuery() = runTest {
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/functions/v1/lookup-food-product", request.url.encodedPath)
            assertEquals(barcode, request.url.parameters["barcode"])
            respond(product, HttpStatusCode.OK, jsonHeaders)
        }
        val client = client(engine)
        try {
            val actual = FoodDiaryRepository(client, { user }).lookup(barcode)
            assertEquals(barcode, actual.barcode)
            assertEquals(BigDecimal("6.40"), actual.perHundred.protein)
        } finally { client.close() }
    }

    @Test fun dayQueriesFilterTheOwnerDateAndHistoricalGoalBoundary() = runTest {
        val engine = MockEngine { request ->
            assertEquals("eq.$user", request.url.parameters["user_id"])
            when (request.url.encodedPath) {
                "/rest/v1/food_entries" -> {
                    assertEquals("eq.$date", request.url.parameters["consumed_on"])
                    respond("[${entry()}]", HttpStatusCode.OK, jsonHeaders)
                }
                "/rest/v1/user_goals" -> {
                    assertEquals("lt.2026-11-08T03:00:00Z", request.url.parameters["effective_at"])
                    respond("""[{"daily_calorie_target":2200,"daily_protein_target_g":140.25,"daily_carbs_target_g":260.75,"daily_fat_target_g":70.01}]""",
                        HttpStatusCode.OK, jsonHeaders)
                }
                else -> error("Unexpected path")
            }
        }
        val client = client(engine)
        try {
            val day = FoodDiaryRepository(client, { user }, { ZoneOffset.ofHours(-3) }).loadDay(date)
            assertEquals(1, day.entries.size)
            assertEquals(BigDecimal("64.00"), day.consumed.calories)
            assertEquals(BigDecimal("140.25"), day.targets!!.proteinGrams)
        } finally { client.close() }
    }

    @Test fun uncertainInsertIsConfirmedByIdAndRetryDoesNotPostAnotherFoodEntry() = runTest {
        var stored = false
        var posts = 0
        val engine = MockEngine { request ->
            when {
                request.url.encodedPath == "/functions/v1/sync-food-product" -> {
                    assertEquals(HttpMethod.Post, request.method)
                    respond("""{"product_id":"$productId"}""", HttpStatusCode.OK, jsonHeaders)
                }
                request.url.encodedPath == "/rest/v1/food_products" ->
                    respond("[$product]", HttpStatusCode.OK, jsonHeaders)
                request.method == HttpMethod.Get -> {
                    assertEquals("eq.$entryId", request.url.parameters["id"])
                    assertEquals("eq.$user", request.url.parameters["user_id"])
                    respond(if (stored) "[${entry()}]" else "[]", HttpStatusCode.OK, jsonHeaders)
                }
                else -> {
                    assertEquals("/rest/v1/food_entries", request.url.encodedPath)
                    assertEquals(HttpMethod.Post, request.method)
                    val raw = Json.parseToJsonElement(request.body.toByteArray().decodeToString())
                    val body = if (raw is JsonArray) raw.single().jsonObject else raw.jsonObject
                    assertEquals(entryId, body["id"]!!.jsonPrimitive.content)
                    assertEquals(user, body["user_id"]!!.jsonPrimitive.content)
                    assertEquals("50", body["quantity"]!!.jsonPrimitive.content)
                    assertFalse(body["quantity"]!!.jsonPrimitive.isString)
                    assertEquals("lunch", body["meal_type"]!!.jsonPrimitive.content)
                    assertEquals(date.toString(), body["consumed_on"]!!.jsonPrimitive.content)
                    assertFalse(body.keys.any { it.endsWith("_snapshot") })
                    posts++
                    stored = true
                    respond("""{"code":"XX000","message":"synthetic failure"}""", HttpStatusCode.InternalServerError, jsonHeaders)
                }
            }
        }
        val client = client(engine)
        try {
            val repo = FoodDiaryRepository(client, { user })
            val request = FoodEntryRequest(entryId, user, barcode, "g", date, MealType.LUNCH, BigDecimal("50"))
            assertEquals(entryId, repo.record(request).id)
            assertEquals(entryId, repo.record(request).id)
            assertEquals(1, posts)
        } finally { client.close() }
    }

    @Test fun paginationDoesNotSilentlyTruncateTheDailyTotal() = runTest {
        var pages = 0
        val engine = MockEngine { request ->
            if (request.url.encodedPath == "/rest/v1/food_entries") {
                pages++
                val body = if (pages == 1) {
                    (1..500).joinToString(",", "[", "]") { entry("entry-$it", "100") }
                } else {
                    assertTrue(request.url.parameters["offset"] == "500" || request.headers[HttpHeaders.Range]?.startsWith("500-") == true)
                    "[${entry("entry-501", "100")}]"
                }
                respond(body, HttpStatusCode.OK, jsonHeaders)
            } else respond("[]", HttpStatusCode.OK, jsonHeaders)
        }
        val client = client(engine)
        try {
            val day = FoodDiaryRepository(client, { user }).loadDay(date)
            assertEquals(2, pages)
            assertEquals(501, day.entries.size)
            assertEquals(BigDecimal("64128.00"), day.consumed.calories)
        } finally { client.close() }
    }
}
