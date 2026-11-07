package com.upupprogress.app.ui.screens.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingFormStateTest {

    private val validForm = OnboardingFormState(
        name = "María José",
        sexForFormula = FormulaSexUi.FEMALE,
        ageYears = 30,
        heightCm = 175,
        weightHundredthsKg = 7_251,
        activityLevel = ActivityLevelUi.ACTIVE,
        personalGoal = PersonalGoalUi.MAINTAIN_WEIGHT,
        nutritionGoalMode = NutritionGoalModeUi.MANUAL,
        manualGoalsSetup = ManualGoalsSetupUi.LATER
    )

    @Test
    fun acceptsCompleteFormWithUnicodeNameAndDecimalWeight() {
        assertTrue(validateOnboardingForm(validForm).isEmpty())
    }

    @Test
    fun emptyFormRequiresEveryPersonalFieldAndSelection() {
        assertEquals(
            setOf(
                OnboardingField.NAME, OnboardingField.FORMULA_SEX, OnboardingField.AGE,
                OnboardingField.HEIGHT, OnboardingField.WEIGHT, OnboardingField.ACTIVITY,
                OnboardingField.GOAL, OnboardingField.NUTRITION_MODE
            ),
            validateOnboardingForm(OnboardingFormState()).keys
        )
    }

    @Test
    fun blankNameIsRejectedWithoutDiscardingOtherFields() {
        assertEquals(
            setOf(OnboardingField.NAME),
            validateOnboardingForm(validForm.copy(name = " \t\n ")).keys
        )
    }

    @Test
    fun rejectsAgeOutsideCurrentInputLimits() {
        listOf(OnboardingInputLimits.MIN_AGE_YEARS - 1, OnboardingInputLimits.MAX_AGE_YEARS + 1).forEach {
            assertEquals(setOf(OnboardingField.AGE), validateOnboardingForm(validForm.copy(ageYears = it)).keys)
        }
    }

    @Test
    fun rejectsHeightOutsideCurrentInputLimits() {
        listOf(OnboardingInputLimits.MIN_HEIGHT_CM - 1, OnboardingInputLimits.MAX_HEIGHT_CM + 1).forEach {
            assertEquals(setOf(OnboardingField.HEIGHT), validateOnboardingForm(validForm.copy(heightCm = it)).keys)
        }
    }

    @Test
    fun rejectsZeroNegativeAndUnrepresentableWeight() {
        listOf(0, -1, OnboardingInputLimits.MAX_WEIGHT_HUNDREDTHS_KG + 1, Int.MAX_VALUE).forEach {
            assertEquals(
                setOf(OnboardingField.WEIGHT),
                validateOnboardingForm(validForm.copy(weightHundredthsKg = it)).keys
            )
        }
    }

    @Test
    fun acceptsInclusiveTechnicalLimits() {
        assertTrue(validateOnboardingForm(validForm.copy(
            ageYears = OnboardingInputLimits.MIN_AGE_YEARS,
            heightCm = OnboardingInputLimits.MIN_HEIGHT_CM,
            weightHundredthsKg = OnboardingInputLimits.MIN_WEIGHT_HUNDREDTHS_KG
        )).isEmpty())
        assertTrue(validateOnboardingForm(validForm.copy(
            ageYears = OnboardingInputLimits.MAX_AGE_YEARS,
            heightCm = OnboardingInputLimits.MAX_HEIGHT_CM,
            weightHundredthsKg = OnboardingInputLimits.MAX_WEIGHT_HUNDREDTHS_KG
        )).isEmpty())
    }

    @Test
    fun eachSingleChoiceGroupRequiresExplicitSelection() {
        listOf(
            validForm.copy(sexForFormula = null) to OnboardingField.FORMULA_SEX,
            validForm.copy(activityLevel = null) to OnboardingField.ACTIVITY,
            validForm.copy(personalGoal = null) to OnboardingField.GOAL
        ).forEach { (form, missingField) ->
            assertEquals(setOf(missingField), validateOnboardingForm(form).keys)
        }
    }
}
