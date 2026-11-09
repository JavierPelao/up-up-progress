package com.upupprogress.app.ui.screens.onboarding

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class OnboardingStepTest {
    private val complete = OnboardingFormState(
        name = "Ejemplo", sexForFormula = FormulaSexUi.FEMALE, ageYears = 34,
        heightCm = 162, weightHundredthsKg = 6050, activityLevel = ActivityLevelUi.LIGHTLY_ACTIVE,
        personalGoal = PersonalGoalUi.LOSE_WEIGHT, nutritionGoalMode = NutritionGoalModeUi.AUTOMATIC,
    )

    @Test fun personalStepDoesNotRequireFutureSelections() {
        val personal = complete.copy(activityLevel = null, personalGoal = null, nutritionGoalMode = null)
        assertTrue(validateOnboardingStep(personal, OnboardingStep.PERSONAL).isEmpty())
        assertFalse(validateOnboardingStep(personal, OnboardingStep.REVIEW).isEmpty())
    }

    @Test fun eachStepRequiresItsOwnSelection() {
        assertEquals(setOf(OnboardingField.ACTIVITY),
            validateOnboardingStep(complete.copy(activityLevel = null), OnboardingStep.ACTIVITY).keys)
        assertEquals(setOf(OnboardingField.GOAL),
            validateOnboardingStep(complete.copy(personalGoal = null), OnboardingStep.GOAL).keys)
        assertEquals(setOf(OnboardingField.NUTRITION_MODE),
            validateOnboardingStep(complete.copy(nutritionGoalMode = null), OnboardingStep.NUTRITION).keys)
    }

    @Test fun automaticLimitsAreExplainedOnTheNutritionStep() {
        val outsideAutomatic = complete.copy(ageYears = 79)
        assertTrue(validateOnboardingStep(outsideAutomatic, OnboardingStep.PERSONAL).isEmpty())
        assertEquals(setOf(OnboardingField.AGE),
            validateOnboardingStep(outsideAutomatic, OnboardingStep.NUTRITION).keys)
        assertEquals(OnboardingStep.NUTRITION, firstStepWithErrors(outsideAutomatic))
    }

    @Test fun reviewChangesRecomputeGoalsWithoutKeepingAnOldEstimate() {
        val edited = complete.copy(weightHundredthsKg = 7000)
        val result = (edited.automaticNutritionResultOrNull() as AutomaticNutritionResult.Estimated).estimate
        assertEquals(0, BigDecimal("112").compareTo(result.proteinGrams))
        assertEquals(1615, result.caloriesKcal)
        assertNotEquals(complete.automaticNutritionResultOrNull(), edited.automaticNutritionResultOrNull())
        assertTrue(validateOnboardingStep(edited, OnboardingStep.REVIEW).isEmpty())
    }

    @Test fun switchingBackAndRestoringKeepsAllTheNonSecretDraft() {
        val manual = complete.copy(nutritionGoalMode = NutritionGoalModeUi.MANUAL,
            manualNutritionDraft = ManualNutritionDraft("2200", "150,55", "250", "65"))
        val restored = restoreOnboardingForm(manual.toSavedValues())
        assertEquals(manual, restored)
        assertTrue(validateOnboardingStep(restored, OnboardingStep.REVIEW).isEmpty())
    }

    @Test fun finalReviewRequiresEveryManualTargetAndTheName() {
        val invalid = complete.copy(name = "", nutritionGoalMode = NutritionGoalModeUi.MANUAL)
        assertEquals(setOf(OnboardingField.NAME, OnboardingField.DAILY_CALORIES,
            OnboardingField.PROTEIN, OnboardingField.CARBS, OnboardingField.FAT),
            validateOnboardingStep(invalid, OnboardingStep.REVIEW).keys)
    }
}
