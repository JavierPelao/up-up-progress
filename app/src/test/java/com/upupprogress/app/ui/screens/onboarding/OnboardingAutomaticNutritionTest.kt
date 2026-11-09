package com.upupprogress.app.ui.screens.onboarding

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingAutomaticNutritionTest {
    private val form = OnboardingFormState(
        name = "Ejemplo",
        sexForFormula = FormulaSexUi.FEMALE,
        ageYears = 34,
        heightCm = 162,
        weightHundredthsKg = 6_050,
        activityLevel = ActivityLevelUi.LIGHTLY_ACTIVE,
        personalGoal = PersonalGoalUi.LOSE_WEIGHT,
        nutritionGoalMode = NutritionGoalModeUi.AUTOMATIC,
    )

    @Test
    fun usesTheCurrentFormValuesWithTheApprovedRules() {
        val result = form.automaticNutritionResultOrNull()
        assertTrue(result is AutomaticNutritionResult.Estimated)
        val value = (result as AutomaticNutritionResult.Estimated).estimate
        assertEquals(0, BigDecimal("1286.5").compareTo(value.bmrKcal))
        assertEquals(0, BigDecimal("1768.9375").compareTo(value.estimatedTdeeKcal))
        assertEquals(-15, value.calorieAdjustmentPercent)
        assertEquals(1504, value.caloriesKcal)
        assertEquals(0, BigDecimal("96.80").compareTo(value.proteinGrams))
        assertEquals(0, BigDecimal("166.40").compareTo(value.carbsGrams))
        assertEquals(0, BigDecimal("50.13").compareTo(value.fatGrams))
        assertTrue(validateOnboardingForm(form).isEmpty())
    }

    @Test
    fun incompleteDataKeepsTheEstimatePendingAndRequiresTheMissingField() {
        val cases = listOf(
            form.copy(sexForFormula = null) to OnboardingField.FORMULA_SEX,
            form.copy(ageYears = null) to OnboardingField.AGE,
            form.copy(heightCm = null) to OnboardingField.HEIGHT,
            form.copy(weightHundredthsKg = null) to OnboardingField.WEIGHT,
            form.copy(activityLevel = null) to OnboardingField.ACTIVITY,
            form.copy(personalGoal = null) to OnboardingField.GOAL,
        )
        cases.forEach { (incomplete, missing) ->
            assertNull(incomplete.automaticNutritionResultOrNull())
            assertEquals(setOf(missing), validateOnboardingForm(incomplete).keys)
        }
    }

    @Test
    fun calculatingAnEstimateDoesNotBypassTheRequiredName() {
        val missingName = form.copy(name = " ")
        assertTrue(missingName.automaticNutritionResultOrNull() is AutomaticNutritionResult.Estimated)
        assertEquals(setOf(OnboardingField.NAME), validateOnboardingForm(missingName).keys)
    }

    @Test
    fun automaticLimitsProduceErrorsOnTheCorrespondingPersonalFields() {
        listOf(
            form.copy(ageYears = 18) to OnboardingField.AGE,
            form.copy(heightCm = 99) to OnboardingField.HEIGHT,
            form.copy(weightHundredthsKg = 2_999) to OnboardingField.WEIGHT,
        ).forEach { (outsideScope, field) ->
            assertEquals(setOf(field), validateOnboardingForm(outsideScope).keys)
            assertTrue(outsideScope.automaticNutritionResultOrNull() is AutomaticNutritionResult.Unavailable)
        }
    }

    @Test
    fun calorieLimitsPreventSuccessfulReviewAndUseTheConfiguredLimitsInMessages() {
        val cases = listOf(
            form.copy(
                ageYears = 78, heightCm = 100, weightHundredthsKg = 3_000,
                activityLevel = ActivityLevelUi.LIGHTLY_ACTIVE,
                personalGoal = PersonalGoalUi.MAINTAIN_WEIGHT,
            ),
            form.copy(
                sexForFormula = FormulaSexUi.MALE,
                ageYears = 19, heightCm = 250, weightHundredthsKg = 30_000,
                activityLevel = ActivityLevelUi.VERY_ACTIVE,
                personalGoal = PersonalGoalUi.GAIN_MUSCLE_OR_WEIGHT,
            ),
        )
        cases.forEach {
            assertEquals(
                AutomaticNutritionResult.Unavailable(AutomaticNutritionIssue.CALORIES_OUT_OF_RANGE),
                it.automaticNutritionResultOrNull()
            )
            val errors = validateOnboardingForm(it)
            assertEquals(setOf(OnboardingField.AUTOMATIC_TARGETS), errors.keys)
            val message = errors.getValue(OnboardingField.AUTOMATIC_TARGETS)
            val limits = OnboardingNutritionConfig.automaticRules.dailyCaloriesRange
            assertTrue(message.contains(limits.first.toString()))
            assertTrue(message.contains(limits.last.toString()))
        }
    }

    @Test
    fun manualModeKeepsItsOwnValidationAndDoesNotReuseAutomaticEstimates() {
        val manual = form.copy(
            ageYears = 79, heightCm = 100, weightHundredthsKg = 2_999,
            nutritionGoalMode = NutritionGoalModeUi.MANUAL,
            manualNutritionDraft = ManualNutritionDraft("2000", "100", "250", "65"),
        )
        assertNull(manual.automaticNutritionResultOrNull())
        assertTrue(manual.manualNutritionTargetsOrNull() != null)
        assertTrue(validateOnboardingForm(manual).isEmpty())
    }

    @Test
    fun restoredFormRecomputesTheEstimateFromSavedInputs() {
        val restored = restoreOnboardingForm(form.toSavedValues())
        assertEquals(form, restored)
        assertEquals(form.automaticNutritionResultOrNull(), restored.automaticNutritionResultOrNull())
        assertTrue(validateOnboardingForm(restored).isEmpty())
    }
}
