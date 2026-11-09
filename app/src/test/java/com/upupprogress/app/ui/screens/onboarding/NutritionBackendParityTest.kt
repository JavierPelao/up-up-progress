package com.upupprogress.app.ui.screens.onboarding

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** El mismo contrato sintético es ejecutado por Kotlin y por las pruebas de la Edge Function. */
class NutritionBackendParityTest {
    @Test
    fun matchesTheSharedNutritionContractCases() {
        val calculator = AutomaticNutritionCalculator(OnboardingNutritionConfig.automaticRules)
        val resource = checkNotNull(javaClass.getResourceAsStream("/nutrition-parity.tsv"))
        var cases = 0
        var estimates = 0
        resource.bufferedReader().useLines { lines ->
            lines.filter { it.isNotBlank() && !it.startsWith("#") }.forEach { line ->
                val values = line.split('\t')
                val input = AutomaticNutritionInput(
                    sexForFormula = FormulaSexUi.entries.first { it.backendValue == values[0] },
                    ageYears = values[1].toInt(),
                    heightCm = values[2].toInt(),
                    weightHundredthsKg = values[3].toBigDecimal().movePointRight(2).intValueExact(),
                    activityLevel = ActivityLevelUi.entries.first { it.backendValue == values[4] },
                    personalGoal = PersonalGoalUi.entries.first { it.backendValue == values[5] },
                )
                val result = calculator.calculate(input)
                if (values[6] == "UNAVAILABLE") {
                    assertEquals(line, AutomaticNutritionResult.Unavailable(AutomaticNutritionIssue.valueOf(values[7])), result)
                } else {
                    assertTrue(line, result is AutomaticNutritionResult.Estimated)
                    val value = (result as AutomaticNutritionResult.Estimated).estimate
                    fun decimal(index: Int, actual: BigDecimal) =
                        assertEquals(line, 0, values[index].toBigDecimal().compareTo(actual))
                    decimal(7, value.bmrKcal)
                    decimal(8, value.estimatedTdeeKcal)
                    assertEquals(line, values[9].toInt(), value.calorieAdjustmentPercent)
                    assertEquals(line, values[10].toInt(), value.caloriesKcal)
                    decimal(11, value.proteinGrams)
                    decimal(12, value.carbsGrams)
                    decimal(13, value.fatGrams)
                    estimates++
                }
                cases++
            }
        }
        assertEquals(810, cases)
        assertTrue(estimates > 0)
    }
}
