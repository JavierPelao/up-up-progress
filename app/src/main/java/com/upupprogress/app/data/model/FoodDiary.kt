package com.upupprogress.app.data.model

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.Duration
import kotlinx.serialization.json.*

enum class MealType(val backendValue: String, val label: String) {
    BREAKFAST("breakfast", "Desayuno"),
    LUNCH("lunch", "Almuerzo"),
    DINNER("dinner", "Cena"),
    SNACK("snack", "Colación"),
}

enum class NutritionBasis(val backendValue: String, val unit: String) {
    PER_100_GRAMS("100g", "g"),
    PER_100_MILLILITERS("100ml", "ml"),
}

/** NULL significa información ausente, nunca un consumo de cero. */
data class FoodNutrients(
    val calories: BigDecimal?,
    val protein: BigDecimal?,
    val carbs: BigDecimal?,
    val fat: BigDecimal?,
) {
    val isComplete: Boolean get() = listOf(calories, protein, carbs, fat).all { it != null && it >= BigDecimal.ZERO }

    fun forQuantity(quantity: BigDecimal): FoodNutrients {
        require(quantity > BigDecimal.ZERO)
        fun BigDecimal?.scaled() = this?.multiply(quantity)?.movePointLeft(2)?.setScale(2, RoundingMode.HALF_UP)
        return FoodNutrients(calories.scaled(), protein.scaled(), carbs.scaled(), fat.scaled())
    }

    operator fun plus(other: FoodNutrients): FoodNutrients {
        fun add(a: BigDecimal?, b: BigDecimal?) = if (a != null && b != null) a + b else null
        return FoodNutrients(add(calories, other.calories), add(protein, other.protein),
            add(carbs, other.carbs), add(fat, other.fat))
    }

    companion object {
        val ZERO = FoodNutrients(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)
    }
}

data class FoodProduct(
    val id: String?,
    val barcode: String,
    val name: String?,
    val brand: String?,
    val basis: NutritionBasis?,
    val perHundred: FoodNutrients,
) {
    val canRecord: Boolean get() = !name.isNullOrBlank() && basis != null && perHundred.isComplete

    companion object {
        fun fromJson(json: JsonObject, expectedBarcode: String? = null): FoodProduct {
            val barcode = requireNotNull(json.string("barcode"))
            require(expectedBarcode == null || barcode == expectedBarcode) { "El código de barras no coincide." }
            fun nutrient(key: String) = json.decimal(key)?.setScale(2, RoundingMode.HALF_UP)
            return FoodProduct(
                json.string("id"), barcode, json.string("name"), json.string("brand"),
                NutritionBasis.entries.firstOrNull { it.backendValue == json.string("nutrition_basis") },
                FoodNutrients(nutrient("energy_kcal_100"), nutrient("protein_g_100"),
                    nutrient("carbs_g_100"), nutrient("fat_g_100")),
            )
        }
    }
}

data class FoodEntry(
    val id: String,
    val userId: String,
    val productId: String,
    val date: LocalDate,
    val meal: MealType,
    val quantity: BigDecimal,
    val unit: String,
    val name: String,
    val perHundred: FoodNutrients,
) {
    val consumed: FoodNutrients get() = perHundred.forQuantity(quantity)

    companion object {
        fun fromJson(json: JsonObject): FoodEntry = FoodEntry(
            id = requireNotNull(json.string("id")),
            userId = requireNotNull(json.string("user_id")),
            productId = requireNotNull(json.string("product_id")),
            date = LocalDate.parse(json.string("consumed_on")),
            meal = MealType.entries.first { it.backendValue == json.string("meal_type") },
            quantity = requireNotNull(json.decimal("quantity")),
            unit = requireNotNull(json.string("unit")),
            name = requireNotNull(json.string("product_name_snapshot")),
            perHundred = FoodNutrients(json.decimal("energy_kcal_100_snapshot"),
                json.decimal("protein_g_100_snapshot"), json.decimal("carbs_g_100_snapshot"),
                json.decimal("fat_g_100_snapshot")),
        )
    }
}

data class FoodDay(val date: LocalDate, val entries: List<FoodEntry>, val targets: NutritionTargets?) {
    val consumed: FoodNutrients get() = entries.fold(FoodNutrients.ZERO) { sum, entry -> sum + entry.consumed }
}

/** El ID permanece estable entre reintentos de la misma confirmación. */
data class FoodEntryRequest(
    val id: String,
    val userId: String,
    val barcode: String,
    val unit: String,
    val date: LocalDate,
    val meal: MealType,
    val quantity: BigDecimal,
) {
    fun toInsert(product: FoodProduct): JsonObject {
        require(product.canRecord && product.barcode == barcode && product.basis?.unit == unit)
        require(parseFoodQuantity(quantity.toPlainString()) != null)
        return buildJsonObject {
            put("id", id)
            put("user_id", userId)
            put("product_id", requireNotNull(product.id))
            put("consumed_on", date.toString())
            put("meal_type", meal.backendValue)
            put("quantity", Json.parseToJsonElement(quantity.toPlainString()))
            put("unit", unit)
            put("nutrition_source", "catalog")
            // El trigger PostgreSQL completa y protege los snapshots nutricionales.
        }
    }
}

private val quantityEdit = Regex("[0-9]{0,8}([.,][0-9]{0,2})?")
fun acceptsFoodQuantityEdit(text: String): Boolean = quantityEdit.matches(text)
fun parseFoodQuantity(text: String): BigDecimal? =
    if (text.isNotBlank() && acceptsFoodQuantityEdit(text)) {
        text.replace(',', '.').toBigDecimalOrNull()?.takeIf { it > BigDecimal.ZERO && it <= BigDecimal("99999999.99") }
    } else null

fun validFoodBarcode(value: String): Boolean = Regex("[0-9]{8,14}").matches(value)

internal fun millisUntilNextFoodDay(now: ZonedDateTime): Long =
    Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay(now.zone)).toMillis().coerceAtLeast(1)

internal fun foodDayEndExclusive(date: LocalDate, zone: ZoneId): String =
    date.plusDays(1).atStartOfDay(zone).toInstant().toString()

internal fun JsonObject.string(key: String): String? = (get(key) as? JsonPrimitive)?.contentOrNull
internal fun JsonObject.decimal(key: String): BigDecimal? = string(key)?.toBigDecimalOrNull()
