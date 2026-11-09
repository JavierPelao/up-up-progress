package com.upupprogress.app.ui.screens.registration

import com.upupprogress.app.data.model.NutritionTargets
import com.upupprogress.app.data.model.OnboardingStatus
import com.upupprogress.app.data.model.OnboardingSubmission
import com.upupprogress.app.ui.screens.onboarding.*
import java.time.LocalDate

internal fun OnboardingFormState.toSubmission(date: LocalDate): OnboardingSubmission {
    require(validateOnboardingForm(this).isEmpty()) { "Formulario incompleto" }
    val manual = manualNutritionTargetsOrNull()
    return OnboardingSubmission(
        displayName = name.trim(), ageYears = requireNotNull(ageYears), heightCm = requireNotNull(heightCm),
        sexForFormula = requireNotNull(sexForFormula).backendValue,
        weightKg = requireNotNull(weightHundredthsKg).toBigDecimal().movePointLeft(2),
        measuredOn = date, activityLevel = requireNotNull(activityLevel).backendValue,
        goalType = requireNotNull(personalGoal).backendValue,
        nutritionGoalMode = if (nutritionGoalMode == NutritionGoalModeUi.MANUAL) "manual" else "automatic",
        manualTargets = manual?.let { NutritionTargets(it.caloriesKcal, it.proteinGrams, it.carbsGrams, it.fatGrams) },
    )
}

internal fun OnboardingStatus.toForm(): OnboardingFormState = OnboardingFormState(
    name = displayName.orEmpty(),
    sexForFormula = FormulaSexUi.entries.firstOrNull { it.backendValue == sexForFormula },
    ageYears = ageYears?.takeIf { it in OnboardingInputLimits.MIN_AGE_YEARS..OnboardingInputLimits.MAX_AGE_YEARS },
    // No truncar en silencio una estatura fraccionaria al restaurarla en el selector entero.
    heightCm = heightCm?.let { runCatching { it.intValueExact() }.getOrNull() },
    weightHundredthsKg = weightKg?.let { runCatching { it.movePointRight(2).intValueExact() }.getOrNull() },
    activityLevel = ActivityLevelUi.entries.firstOrNull { it.backendValue == activityLevel },
    personalGoal = PersonalGoalUi.entries.firstOrNull { it.backendValue == goalType },
    nutritionGoalMode = when (nutritionGoalMode) {
        "manual" -> NutritionGoalModeUi.MANUAL
        "automatic" -> NutritionGoalModeUi.AUTOMATIC
        else -> null
    },
    manualNutritionDraft = if (nutritionGoalMode == "manual" && targets != null) ManualNutritionDraft(
        targets.caloriesKcal.toString(), targets.proteinGrams.toPlainString(),
        targets.carbsGrams.toPlainString(), targets.fatGrams.toPlainString()
    ) else ManualNutritionDraft(),
)
