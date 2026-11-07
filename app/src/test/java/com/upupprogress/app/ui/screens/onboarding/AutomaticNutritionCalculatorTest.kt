package com.upupprogress.app.ui.screens.onboarding

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomaticNutritionCalculatorTest {
    // Casos conocidos de la configuración aprobada: revisar las expectativas si cambian las reglas.
    private val rules = OnboardingNutritionConfig.automaticRules
    private val calculator = AutomaticNutritionCalculator(rules)
    private val input = AutomaticNutritionInput(
        sexForFormula = FormulaSexUi.MALE,
        ageYears = 30,
        heightCm = 175,
        weightHundredthsKg = 7_000,
        activityLevel = ActivityLevelUi.ACTIVE,
        personalGoal = PersonalGoalUi.MAINTAIN_WEIGHT,
    )

    @Test
    fun matchesKnownMaleMifflinExample() {
        val estimate = estimate()
        assertDecimal("1648.75", estimate.bmrKcal)
        assertDecimal("2473.125", estimate.estimatedTdeeKcal)
        assertEquals(2473, estimate.caloriesKcal)
        assertDecimal("126.00", estimate.proteinGrams)
        assertDecimal("306.78", estimate.carbsGrams)
        assertDecimal("82.43", estimate.fatGrams)
    }

    @Test
    fun femaleCoefficientChangesBmrBy166Kcal() {
        val female = estimate(input.copy(sexForFormula = FormulaSexUi.FEMALE))
        assertDecimal("1482.75", female.bmrKcal)
        assertDecimal("2224.125", female.estimatedTdeeKcal)
        assertEquals(2224, female.caloriesKcal)
        assertDecimal("166", estimate().bmrKcal - female.bmrKcal)
        assertDecimal("126.00", female.proteinGrams)
        assertDecimal("263.20", female.carbsGrams)
        assertDecimal("74.13", female.fatGrams)
    }

    @Test
    fun preservesHundredthsOfWeightAndIntermediatePrecision() {
        val fractional = input.copy(
            weightHundredthsKg = 7_025,
            activityLevel = ActivityLevelUi.LIGHTLY_ACTIVE,
            personalGoal = PersonalGoalUi.LOSE_WEIGHT,
        )
        val result = estimate(fractional)
        assertDecimal("1651.25", result.bmrKcal)
        assertDecimal("2270.46875", result.estimatedTdeeKcal)
        assertEquals(1930, result.caloriesKcal)
        assertDecimal("112.40", result.proteinGrams)
        // Redondear antes el TDEE a 2270 daría 2497 al aumentar; el resultado correcto es 2498.
        assertEquals(2498, estimate(fractional.copy(personalGoal = PersonalGoalUi.GAIN_MUSCLE_OR_WEIGHT)).caloriesKcal)
    }

    @Test
    fun appliesTheCurrentActivityFactors() {
        val expectedTdee = mapOf(
            ActivityLevelUi.SEDENTARY to "1978.5",
            ActivityLevelUi.LIGHTLY_ACTIVE to "2267.03125",
            ActivityLevelUi.ACTIVE to "2473.125",
            ActivityLevelUi.VERY_ACTIVE to "3132.625",
        )
        expectedTdee.forEach { (activity, expected) ->
            assertDecimal(expected, estimate(input.copy(activityLevel = activity)).estimatedTdeeKcal)
        }
    }

    @Test
    fun appliesEachGoalAdjustmentBeforeRounding() {
        val expected = mapOf(
            PersonalGoalUi.GAIN_MUSCLE_OR_WEIGHT to (10 to 2720),
            PersonalGoalUi.LOSE_WEIGHT to (-15 to 2102),
            PersonalGoalUi.MAINTAIN_WEIGHT to (0 to 2473),
        )
        expected.forEach { (goal, values) ->
            val result = estimate(input.copy(personalGoal = goal))
            assertEquals(values.first, result.calorieAdjustmentPercent)
            assertEquals(values.second, result.caloriesKcal)
        }
    }

    @Test
    fun roundsHalfKcalUpAndAllocatesRemainingEnergyFromTheIntegerTarget() {
        val result = estimate(input.copy(activityLevel = ActivityLevelUi.SEDENTARY))
        assertDecimal("1978.5", result.estimatedTdeeKcal)
        assertEquals(1979, result.caloriesKcal)
        assertDecimal("84.00", result.proteinGrams)
        assertDecimal("262.33", result.carbsGrams)
        assertDecimal("65.97", result.fatGrams)
        assertDecimal("1979.05", result.caloriesFromMacrosKcal)
    }

    @Test
    fun rejectsAgeOutsideTheProposedScope() {
        listOf(18, 79, 0, Int.MIN_VALUE, Int.MAX_VALUE).forEach {
            assertUnavailable(AutomaticNutritionIssue.AGE_OUT_OF_RANGE, input.copy(ageYears = it))
        }
    }

    @Test
    fun rejectsHeightOutsideTheProposedScope() {
        listOf(99, 251, 0, Int.MIN_VALUE, Int.MAX_VALUE).forEach {
            assertUnavailable(AutomaticNutritionIssue.HEIGHT_OUT_OF_RANGE, input.copy(heightCm = it))
        }
    }

    @Test
    fun rejectsWeightOutsideTheProposedScopeWithoutOverflow() {
        listOf(2_999, 30_001, 0, Int.MIN_VALUE, Int.MAX_VALUE).forEach {
            assertUnavailable(AutomaticNutritionIssue.WEIGHT_OUT_OF_RANGE, input.copy(weightHundredthsKg = it))
        }
    }

    @Test
    fun acceptsInclusiveInputLimitsWhenTheResultRemainsInScope() {
        val cases = listOf(
            input.copy(ageYears = 19),
            input.copy(ageYears = 78),
            input.copy(heightCm = 100, weightHundredthsKg = 10_000),
            input.copy(heightCm = 250),
            input.copy(weightHundredthsKg = 3_000, activityLevel = ActivityLevelUi.VERY_ACTIVE),
            input.copy(
                weightHundredthsKg = 30_000,
                heightCm = 100,
                ageYears = 78,
                activityLevel = ActivityLevelUi.SEDENTARY,
            ),
        )
        cases.forEach { estimate(it) }
    }

    @Test
    fun refusesTooLowAndTooHighCaloriesRatherThanClamping() {
        assertUnavailable(
            AutomaticNutritionIssue.CALORIES_OUT_OF_RANGE,
            input.copy(
                sexForFormula = FormulaSexUi.FEMALE, ageYears = 78, heightCm = 100,
                weightHundredthsKg = 3_000, activityLevel = ActivityLevelUi.SEDENTARY,
            ),
        )
        assertUnavailable(
            AutomaticNutritionIssue.CALORIES_OUT_OF_RANGE,
            input.copy(
                ageYears = 19, heightCm = 250, weightHundredthsKg = 30_000,
                activityLevel = ActivityLevelUi.VERY_ACTIVE,
            ),
        )
    }

    @Test
    fun calorieLimitsApplyInclusivelyToTheRoundedTarget() {
        val exactRange = AutomaticNutritionCalculator(rules.copy(dailyCaloriesRange = 2473..2473))
        assertTrue(exactRange.calculate(input) is AutomaticNutritionResult.Estimated)
        listOf(2474..5000, 1500..2472).forEach {
            assertEquals(
                AutomaticNutritionResult.Unavailable(AutomaticNutritionIssue.CALORIES_OUT_OF_RANGE),
                AutomaticNutritionCalculator(rules.copy(dailyCaloriesRange = it)).calculate(input),
            )
        }
    }

    @Test
    fun macroEnergyMatchesAcrossSexActivityGoalAndDecimalWeight() {
        var estimatesChecked = 0
        FormulaSexUi.entries.forEach { sex ->
            ActivityLevelUi.entries.forEach { activity ->
                PersonalGoalUi.entries.forEach { goal ->
                    listOf(3_000, 7_000, 7_025, 30_000).forEach { weight ->
                        when (val result = calculator.calculate(input.copy(
                            sexForFormula = sex, activityLevel = activity,
                            personalGoal = goal, weightHundredthsKg = weight,
                        ))) {
                            is AutomaticNutritionResult.Unavailable ->
                                assertEquals(AutomaticNutritionIssue.CALORIES_OUT_OF_RANGE, result.issue)
                            is AutomaticNutritionResult.Estimated -> {
                                val value = result.estimate
                                val reconstructed = value.proteinGrams * BigDecimal("4") +
                                    value.carbsGrams * BigDecimal("4") + value.fatGrams * BigDecimal("9")
                                assertTrue((value.caloriesKcal.toBigDecimal() - reconstructed).abs() <= BigDecimal("0.09"))
                                listOf(value.proteinGrams, value.carbsGrams, value.fatGrams).forEach {
                                    assertEquals(2, it.scale())
                                    assertTrue(it >= BigDecimal.ZERO)
                                }
                                estimatesChecked++
                            }
                        }
                    }
                }
            }
        }
        assertTrue(estimatesChecked > 0)
    }

    @Test
    fun rejectsNonPositiveBmrEvenIfInputRangesWereBroadened() {
        val broadRules = rules.copy(heightCmRange = 1..250, weightHundredthsKgRange = 1..30_000)
        assertEquals(
            AutomaticNutritionResult.Unavailable(AutomaticNutritionIssue.NON_POSITIVE_BMR),
            AutomaticNutritionCalculator(broadRules).calculate(input.copy(
                sexForFormula = FormulaSexUi.FEMALE,
                ageYears = 78, heightCm = 1, weightHundredthsKg = 1,
            )),
        )
    }

    @Test
    fun appliesEveryCellOfTheProteinTableForSeventyKg() {
        val cases = listOf(
            Triple(ActivityLevelUi.SEDENTARY, PersonalGoalUi.MAINTAIN_WEIGHT, "84.00"),
            Triple(ActivityLevelUi.SEDENTARY, PersonalGoalUi.LOSE_WEIGHT, "98.00"),
            Triple(ActivityLevelUi.SEDENTARY, PersonalGoalUi.GAIN_MUSCLE_OR_WEIGHT, "112.00"),
            Triple(ActivityLevelUi.LIGHTLY_ACTIVE, PersonalGoalUi.MAINTAIN_WEIGHT, "98.00"),
            Triple(ActivityLevelUi.LIGHTLY_ACTIVE, PersonalGoalUi.LOSE_WEIGHT, "112.00"),
            Triple(ActivityLevelUi.LIGHTLY_ACTIVE, PersonalGoalUi.GAIN_MUSCLE_OR_WEIGHT, "112.00"),
            Triple(ActivityLevelUi.ACTIVE, PersonalGoalUi.MAINTAIN_WEIGHT, "126.00"),
            Triple(ActivityLevelUi.ACTIVE, PersonalGoalUi.LOSE_WEIGHT, "140.00"),
            Triple(ActivityLevelUi.ACTIVE, PersonalGoalUi.GAIN_MUSCLE_OR_WEIGHT, "126.00"),
            Triple(ActivityLevelUi.VERY_ACTIVE, PersonalGoalUi.MAINTAIN_WEIGHT, "140.00"),
            Triple(ActivityLevelUi.VERY_ACTIVE, PersonalGoalUi.LOSE_WEIGHT, "154.00"),
            Triple(ActivityLevelUi.VERY_ACTIVE, PersonalGoalUi.GAIN_MUSCLE_OR_WEIGHT, "140.00"),
        )
        cases.forEach { (activity, goal, expectedGrams) ->
            assertDecimal(expectedGrams, estimate(input.copy(activityLevel = activity, personalGoal = goal)).proteinGrams)
        }
    }

    @Test
    fun roundsProteinBeforeReservingItsEnergyForTheRemainder() {
        val result = estimate(input.copy(
            weightHundredthsKg = 7_028,
            activityLevel = ActivityLevelUi.SEDENTARY,
            personalGoal = PersonalGoalUi.LOSE_WEIGHT,
        ))
        assertEquals(1685, result.caloriesKcal)
        assertDecimal("98.39", result.proteinGrams)
        assertDecimal("196.49", result.carbsGrams)
        assertDecimal("56.17", result.fatGrams)
    }

    @Test
    fun changingOneProteinCoefficientUpdatesProteinAndRemainingCarbs() {
        val customRules = rulesWithProtein(ActivityLevelUi.ACTIVE, PersonalGoalUi.MAINTAIN_WEIGHT, "2.1")
        val result = AutomaticNutritionCalculator(customRules).calculate(input)
        assertTrue(result is AutomaticNutritionResult.Estimated)
        val value = (result as AutomaticNutritionResult.Estimated).estimate
        assertEquals(2473, value.caloriesKcal)
        assertDecimal("147.00", value.proteinGrams)
        assertDecimal("285.78", value.carbsGrams)
        assertDecimal("82.43", value.fatGrams)
    }

    @Test
    fun changingFatPercentageUpdatesFatAndRemainingCarbs() {
        val result = AutomaticNutritionCalculator(rules.copy(fatEnergyPercent = 35)).calculate(input)
        assertTrue(result is AutomaticNutritionResult.Estimated)
        val value = (result as AutomaticNutritionResult.Estimated).estimate
        assertDecimal("126.00", value.proteinGrams)
        assertDecimal("275.86", value.carbsGrams)
        assertDecimal("96.17", value.fatGrams)
    }

    @Test
    fun rejectsProteinAndFatThatExceedCaloriesWithoutReducingProtein() {
        val syntheticRules = rulesWithProtein(ActivityLevelUi.ACTIVE, PersonalGoalUi.MAINTAIN_WEIGHT, "100")
        assertEquals(
            AutomaticNutritionResult.Unavailable(AutomaticNutritionIssue.MACROS_EXCEED_CALORIE_TARGET),
            AutomaticNutritionCalculator(syntheticRules).calculate(input),
        )
    }

    @Test
    fun exactEnergyBalanceAllowsDefinedZeroCarbs() {
        // Configuración sintética para verificar el límite matemático, no una regla de la aplicación.
        val syntheticRules = rulesWithProtein(ActivityLevelUi.SEDENTARY, PersonalGoalUi.MAINTAIN_WEIGHT, "4.2")
        val result = AutomaticNutritionCalculator(syntheticRules).calculate(input.copy(
            ageYears = 51, heightCm = 200, weightHundredthsKg = 10_000,
            activityLevel = ActivityLevelUi.SEDENTARY,
        ))
        assertTrue(result is AutomaticNutritionResult.Estimated)
        val value = (result as AutomaticNutritionResult.Estimated).estimate
        assertEquals(2400, value.caloriesKcal)
        assertDecimal("420.00", value.proteinGrams)
        assertDecimal("0.00", value.carbsGrams)
        assertDecimal("80.00", value.fatGrams)
        assertDecimal("2400", value.caloriesFromMacrosKcal)
    }

    @Test
    fun rejectsIncompleteOrMathematicallyInvalidRules() {
        val activeProtein = rules.proteinGramsPerKg.getValue(ActivityLevelUi.ACTIVE)
        val invalidRules = listOf<() -> AutomaticNutritionRules>(
            { rules.copy(activityFactors = rules.activityFactors - ActivityLevelUi.ACTIVE) },
            { rules.copy(activityFactors = rules.activityFactors + (ActivityLevelUi.ACTIVE to BigDecimal.ZERO)) },
            { rules.copy(goalAdjustmentPercent = rules.goalAdjustmentPercent - PersonalGoalUi.LOSE_WEIGHT) },
            { rules.copy(goalAdjustmentPercent = rules.goalAdjustmentPercent + (PersonalGoalUi.LOSE_WEIGHT to -100)) },
            { rules.copy(goalAdjustmentPercent = rules.goalAdjustmentPercent + (PersonalGoalUi.GAIN_MUSCLE_OR_WEIGHT to 0)) },
            { rules.copy(goalAdjustmentPercent = rules.goalAdjustmentPercent + (PersonalGoalUi.MAINTAIN_WEIGHT to 1)) },
            { rules.copy(proteinGramsPerKg = rules.proteinGramsPerKg - ActivityLevelUi.ACTIVE) },
            { rules.copy(proteinGramsPerKg = rules.proteinGramsPerKg + (ActivityLevelUi.ACTIVE to (activeProtein - PersonalGoalUi.MAINTAIN_WEIGHT))) },
            { rulesWithProtein(ActivityLevelUi.ACTIVE, PersonalGoalUi.MAINTAIN_WEIGHT, "0") },
            { rulesWithProtein(ActivityLevelUi.ACTIVE, PersonalGoalUi.LOSE_WEIGHT, "-1") },
            { rules.copy(fatEnergyPercent = 0) },
            { rules.copy(fatEnergyPercent = 100) },
            { rules.copy(ageYearsRange = 1..0) },
            { rules.copy(heightCmRange = 0..250) },
            { rules.copy(weightHundredthsKgRange = -1..30_000) },
            { rules.copy(dailyCaloriesRange = 0..5000) },
        )
        invalidRules.forEach {
            assertTrue(runCatching { it() }.exceptionOrNull() is IllegalArgumentException)
        }
    }

    private fun rulesWithProtein(
        activity: ActivityLevelUi,
        goal: PersonalGoalUi,
        coefficient: String,
    ): AutomaticNutritionRules = rules.copy(
        proteinGramsPerKg = rules.proteinGramsPerKg + (
            activity to (rules.proteinGramsPerKg.getValue(activity) + (goal to BigDecimal(coefficient)))
        )
    )

    private fun estimate(value: AutomaticNutritionInput = input): AutomaticNutritionEstimate {
        val result = calculator.calculate(value)
        assertTrue("Se esperaba una estimación local.", result is AutomaticNutritionResult.Estimated)
        return (result as AutomaticNutritionResult.Estimated).estimate
    }

    private fun assertUnavailable(issue: AutomaticNutritionIssue, value: AutomaticNutritionInput) {
        assertEquals(AutomaticNutritionResult.Unavailable(issue), calculator.calculate(value))
    }

    private fun assertDecimal(expected: String, actual: BigDecimal) {
        assertEquals("Se esperaba $expected.", 0, BigDecimal(expected).compareTo(actual))
    }
}
