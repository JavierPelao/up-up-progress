package com.upupprogress.app.ui.screens.onboarding

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualNutritionGoalsTest {
    private val draft = ManualNutritionDraft("2200", "150,25", "250.50", "65")
    private val form = OnboardingFormState(
        name = "María José",
        sexForFormula = FormulaSexUi.FEMALE,
        ageYears = 30,
        heightCm = 175,
        weightHundredthsKg = 7_251,
        activityLevel = ActivityLevelUi.ACTIVE,
        personalGoal = PersonalGoalUi.MAINTAIN_WEIGHT,
        nutritionGoalMode = NutritionGoalModeUi.MANUAL,
        manualGoalsSetup = ManualGoalsSetupUi.NOW,
        manualNutritionDraft = draft
    )

    @Test
    fun parsesCommaAndPointWithoutLosingDecimalPrecision() {
        assertEquals(
            ManualNutritionTargets(2200, BigDecimal("150.25"), BigDecimal("250.50"), BigDecimal("65")),
            parseManualNutritionTargets(draft)
        )
        assertTrue(validateOnboardingForm(form).isEmpty())
    }

    @Test
    fun rejectsInvalidPastesRatherThanChangingTheirMeaning() {
        listOf("1e3", "-1", "+1", "12 kcal", " 100", "1 000", "NaN", "Infinity", "１２").forEach { text ->
            ManualNutritionField.entries.forEach { field ->
                assertFalse("$field: $text", acceptsManualNutritionEdit(field, text))
            }
        }
    }

    @Test
    fun caloriesAcceptOnlyIntegerDigitsAndBoundedLength() {
        assertTrue(acceptsManualNutritionEdit(ManualNutritionField.CALORIES, "2200"))
        listOf("1.5", "1,5", "100000", "2147483648").forEach {
            assertFalse(acceptsManualNutritionEdit(ManualNutritionField.CALORIES, it))
        }
    }

    @Test
    fun macrosRejectMultipleSeparatorsAndExcessDecimalPlaces() {
        listOf("1,2.3", "1..2", "1,,2", "0.001", "0,001", "10000").forEach {
            assertFalse(acceptsManualNutritionEdit(ManualNutritionField.PROTEIN, it))
        }
    }

    @Test
    fun allowsTemporaryEditingStatesButRequiresANumberBeforeReview() {
        ManualNutritionField.entries.forEach {
            assertTrue(acceptsManualNutritionEdit(it, ""))
        }
        listOf(".", ",").forEach {
            assertTrue(acceptsManualNutritionEdit(ManualNutritionField.PROTEIN, it))
            assertNull(parseManualNutritionTargets(draft.copy(proteinGrams = it)))
        }
        assertTrue(acceptsManualNutritionEdit(ManualNutritionField.PROTEIN, "1,"))
        assertEquals(BigDecimal.ONE, parseManualNutritionTargets(draft.copy(proteinGrams = "1,"))?.proteinGrams)
    }

    @Test
    fun rejectsCaloriesOutsideLimitsEvenWhenParsingSucceeds() {
        listOf("0", "20001", "99999", "-1", "1.5").forEach {
            assertEquals(
                setOf(ManualNutritionField.CALORIES),
                validateManualNutritionDraft(draft.copy(calories = it)).keys
            )
            assertNull(parseManualNutritionTargets(draft.copy(calories = it)))
        }
    }

    @Test
    fun rejectsInvalidOrOutOfRangeMacrosInEachField() {
        ManualNutritionField.entries.filter { it != ManualNutritionField.CALORIES }.forEach { field ->
            listOf("-0.01", "5000.01", "5001", ".", "NaN", "1e2", "0.001").forEach {
                val edited = draft.withValue(field, it)
                assertEquals(setOf(field), validateManualNutritionDraft(edited).keys)
                assertNull(parseManualNutritionTargets(edited))
            }
        }
    }

    @Test
    fun acceptsInclusiveInputBoundsAndExplicitZeroMacros() {
        val minimum = ManualNutritionDraft(
            ManualNutritionInputLimits.MIN_CALORIES_KCAL.toString(), "0", "0", "0"
        )
        val maximum = ManualNutritionDraft(
            ManualNutritionInputLimits.MAX_CALORIES_KCAL.toString(),
            ManualNutritionInputLimits.MAX_MACRO_GRAMS.toString(),
            ManualNutritionInputLimits.MAX_MACRO_GRAMS.toString(),
            ManualNutritionInputLimits.MAX_MACRO_GRAMS.toString()
        )
        assertNotNull(parseManualNutritionTargets(minimum))
        assertEquals(BigDecimal.ZERO, parseManualNutritionTargets(minimum)?.proteinGrams)
        assertNotNull(parseManualNutritionTargets(maximum))
    }

    @Test
    fun manualNowRequiresAllFourTargets() {
        assertEquals(
            setOf(OnboardingField.DAILY_CALORIES, OnboardingField.PROTEIN, OnboardingField.CARBS, OnboardingField.FAT),
            validateOnboardingForm(form.copy(manualNutritionDraft = ManualNutritionDraft())).keys
        )
        ManualNutritionField.entries.forEach {
            assertNull(form.copy(manualNutritionDraft = draft.withValue(it, "")).manualNutritionTargetsOrNull())
        }
    }

    @Test
    fun requiresAnExplicitModeAndManualSetupChoice() {
        assertEquals(
            setOf(OnboardingField.NUTRITION_MODE),
            validateOnboardingForm(form.copy(nutritionGoalMode = null)).keys
        )
        assertEquals(
            setOf(OnboardingField.MANUAL_SETUP),
            validateOnboardingForm(form.copy(manualGoalsSetup = null)).keys
        )
    }

    @Test
    fun laterIgnoresUnfinishedDraftAndLeavesEffectiveTargetsUndefined() {
        val later = form.copy(
            manualGoalsSetup = ManualGoalsSetupUi.LATER,
            manualNutritionDraft = draft.copy(calories = "0", proteinGrams = "")
        )
        assertTrue(validateOnboardingForm(later).isEmpty())
        assertNull(later.manualNutritionTargetsOrNull())
        assertNull(form.copy(manualGoalsSetup = ManualGoalsSetupUi.LATER).manualNutritionTargetsOrNull())
    }

    @Test
    fun automaticDoesNotRequireOrReuseManualValues() {
        val automatic = form.copy(
            nutritionGoalMode = NutritionGoalModeUi.AUTOMATIC,
            manualNutritionDraft = draft.copy(calories = "0")
        )
        assertTrue(validateOnboardingForm(automatic).isEmpty())
        assertNull(automatic.manualNutritionTargetsOrNull())
        assertNull(form.copy(nutritionGoalMode = NutritionGoalModeUi.AUTOMATIC).manualNutritionTargetsOrNull())
    }

    @Test
    fun changingModesKeepsDraftAvailableWhenReturningToManualNow() {
        val switched = form.copy(nutritionGoalMode = NutritionGoalModeUi.AUTOMATIC)
            .copy(nutritionGoalMode = NutritionGoalModeUi.MANUAL, manualGoalsSetup = ManualGoalsSetupUi.LATER)
            .copy(manualGoalsSetup = ManualGoalsSetupUi.NOW)
        assertEquals(draft, switched.manualNutritionDraft)
        assertEquals(form.manualNutritionTargetsOrNull(), switched.manualNutritionTargetsOrNull())
    }

    @Test
    fun savedStateRestoresChoicesProfileAndUnfinishedDecimalText() {
        val unfinished = form.copy(manualNutritionDraft = draft.copy(fatGrams = "65,"))
        assertEquals(unfinished, restoreOnboardingForm(unfinished.toSavedValues()))
        val later = unfinished.copy(manualGoalsSetup = ManualGoalsSetupUi.LATER)
        assertEquals(later, restoreOnboardingForm(later.toSavedValues()))
        assertNull(restoreOnboardingForm(later.toSavedValues()).manualNutritionTargetsOrNull())
    }

    @Test
    fun restoresPreviousStagePersonalDataWithoutInventingNutritionChoices() {
        val restored = restoreOnboardingForm(form.toSavedValues().take(7))
        assertEquals(
            form.copy(
                nutritionGoalMode = null,
                manualGoalsSetup = null,
                manualNutritionDraft = ManualNutritionDraft()
            ),
            restored
        )
    }
}
