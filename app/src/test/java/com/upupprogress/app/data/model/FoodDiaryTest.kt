package com.upupprogress.app.data.model

import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class FoodDiaryTest {
    private val date = LocalDate.of(2026, 11, 8)
    private val bread = FoodNutrients(BigDecimal("128"), BigDecimal("6.40"), BigDecimal("21.30"), BigDecimal("1.50"))

    @Test fun quantityScalesEveryNutrientAndUsesTwoDecimals() {
        val actual = bread.forQuantity(BigDecimal("35.50"))
        assertEquals(BigDecimal("45.44"), actual.calories)
        assertEquals(BigDecimal("2.27"), actual.protein)
        assertEquals(BigDecimal("7.56"), actual.carbs)
        assertEquals(BigDecimal("0.53"), actual.fat)
    }

    @Test fun drinksUseTheSamePerHundredRuleWithoutConvertingMlIntoGrams() {
        val drink = FoodProduct("p", "12345678", "Ejemplo", null, NutritionBasis.PER_100_MILLILITERS, bread)
        val request = FoodEntryRequest("entry", "owner", drink.barcode, "ml", date, MealType.SNACK, BigDecimal("250"))
        val body = request.toInsert(drink)
        assertEquals("ml", body["unit"]!!.jsonPrimitive.content)
        assertEquals(BigDecimal("320.00"), drink.perHundred.forQuantity(request.quantity).calories)
        assertThrows(IllegalArgumentException::class.java) { request.copy(unit = "g").toInsert(drink) }
    }

    @Test fun previewRoundsTheCatalogBaseBeforeMultiplyingLikeNumericTenTwo() {
        val product = FoodProduct.fromJson(Json.parseToJsonElement("""
            {"barcode":"12345678","name":"Ejemplo","nutrition_basis":"100g",
             "energy_kcal_100":128,"protein_g_100":6.405,"carbs_g_100":21.3,"fat_g_100":1.5}
        """).jsonObject, "12345678")
        assertEquals(BigDecimal("6.41"), product.perHundred.protein)
        assertEquals(BigDecimal("3.21"), product.perHundred.forQuantity(BigDecimal("50")).protein)
    }

    @Test fun missingNutrientsStayUnknownAndCannotBeRecorded() {
        val missing = bread.copy(protein = null)
        assertNull(missing.forQuantity(BigDecimal("50")).protein)
        assertNull((FoodNutrients.ZERO + missing).protein)
        assertFalse(FoodProduct("p", "12345678", "Ejemplo", null, NutritionBasis.PER_100_GRAMS, missing).canRecord)
        assertTrue(FoodProduct("p", "12345678", "Ejemplo", null, NutritionBasis.PER_100_GRAMS, FoodNutrients.ZERO).canRecord)
    }

    @Test fun dailyTotalsUseEachHistoricalSnapshotAndItsQuantity() {
        val a = FoodEntry("a", "owner", "p", date, MealType.BREAKFAST, BigDecimal("50"), "g", "Antes", bread)
        val b = a.copy(id = "b", quantity = BigDecimal("100"), perHundred = bread.copy(calories = BigDecimal("200")))
        val day = FoodDay(date, listOf(a, b), null)
        assertEquals(BigDecimal("264.00"), day.consumed.calories)
        assertEquals(BigDecimal("9.60"), day.consumed.protein)
        assertEquals(FoodNutrients.ZERO, FoodDay(date, emptyList(), null).consumed)
    }

    @Test fun quantityRejectsInvalidPrecisionNonPositiveAndOverflow() {
        assertEquals(BigDecimal("1.25"), parseFoodQuantity("1,25"))
        assertEquals(BigDecimal("0.01"), parseFoodQuantity("0.01"))
        assertNotNull(parseFoodQuantity("99999999.99"))
        listOf("", ".", "0", "-1", "1e2", "NaN", "1.234", "100000000", "Infinity").forEach {
            assertNull(it, parseFoodQuantity(it))
        }
    }

    @Test fun insertHasExactQuantityAndLeavesSnapshotsToPostgreSql() {
        val product = FoodProduct("product", "12345678", "Ejemplo", null, NutritionBasis.PER_100_GRAMS, bread)
        val request = FoodEntryRequest("stable-id", "owner", product.barcode, "g", date, MealType.LUNCH, BigDecimal("35.50"))
        val body = request.toInsert(product)
        assertEquals("35.50", body["quantity"]!!.jsonPrimitive.content)
        assertFalse(body["quantity"]!!.jsonPrimitive.isString)
        assertEquals("lunch", body["meal_type"]!!.jsonPrimitive.content)
        assertEquals("2026-11-08", body["consumed_on"]!!.jsonPrimitive.content)
        assertEquals("catalog", body["nutrition_source"]!!.jsonPrimitive.content)
        assertFalse(body.keys.any { it.endsWith("_snapshot") })
    }

    @Test fun barcodesNeverAcceptArbitraryTextOrUrls() {
        assertTrue(validFoodBarcode("12345678"))
        assertTrue(validFoodBarcode("01234567890123"))
        listOf("https://example.invalid", "1234567", "123456789012345", "abcd1234").forEach {
            assertFalse(validFoodBarcode(it))
        }
    }

    @Test fun nextDayUsesZoneRulesForTwentyThreeAndTwentyFiveHourDays() {
        val zone = ZoneId.of("America/New_York")
        assertEquals(23 * 60 * 60 * 1000L, millisUntilNextFoodDay(LocalDate.of(2026, 3, 8).atStartOfDay(zone)))
        assertEquals(25 * 60 * 60 * 1000L, millisUntilNextFoodDay(LocalDate.of(2026, 11, 1).atStartOfDay(zone)))
        assertEquals("2026-11-08T10:00:00Z", foodDayEndExclusive(date, ZoneId.of("Pacific/Kiritimati")))
    }
}
