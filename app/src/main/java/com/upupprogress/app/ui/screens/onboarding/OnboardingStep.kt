package com.upupprogress.app.ui.screens.onboarding

enum class OnboardingStep(val title: String, val description: String) {
    PERSONAL("Datos personales", "Estos datos nos ayudan a estimar tus necesidades."),
    ACTIVITY("Actividad física", "Elige la opción que mejor describe tu semana habitual."),
    GOAL("Tu objetivo", "¿Qué te gustaría conseguir?"),
    NUTRITION("Metas nutricionales", "Elige entre definir tus metas o utilizar una estimación."),
    REVIEW("Revisa tus datos", "Puedes corregir cualquier sección antes de confirmar."),
}

internal fun ActivityLevelUi.description(): String = when (this) {
    ActivityLevelUi.LIGHTLY_ACTIVE -> "Caminas y realizas actividad física ligera algunos días."
    ActivityLevelUi.ACTIVE -> "Te mantienes en movimiento y entrenas con regularidad."
    ActivityLevelUi.VERY_ACTIVE -> "Realizas entrenamientos intensos o una actividad diaria muy exigente."
}

/** Valida solo la sección visible; los campos de los siguientes pasos pueden estar vacíos. */
internal fun validateOnboardingStep(form: OnboardingFormState, step: OnboardingStep): Map<OnboardingField, String> {
    val fields = when (step) {
        OnboardingStep.PERSONAL -> setOf(OnboardingField.FORMULA_SEX, OnboardingField.AGE, OnboardingField.HEIGHT, OnboardingField.WEIGHT)
        OnboardingStep.ACTIVITY -> setOf(OnboardingField.ACTIVITY)
        OnboardingStep.GOAL -> setOf(OnboardingField.GOAL)
        OnboardingStep.NUTRITION -> setOf(OnboardingField.NUTRITION_MODE,
            OnboardingField.DAILY_CALORIES, OnboardingField.PROTEIN, OnboardingField.CARBS,
            OnboardingField.FAT, OnboardingField.AUTOMATIC_TARGETS,
            OnboardingField.AGE, OnboardingField.HEIGHT, OnboardingField.WEIGHT)
        OnboardingStep.REVIEW -> OnboardingField.entries.toSet()
    }
    // Los límites automáticos se comprueban al elegir modalidad, sin bloquear los pasos previos.
    val draft = if (step in listOf(OnboardingStep.PERSONAL, OnboardingStep.ACTIVITY, OnboardingStep.GOAL))
        form.copy(nutritionGoalMode = null) else form
    return validateOnboardingForm(draft).filterKeys { it in fields }
}

internal fun firstStepWithErrors(form: OnboardingFormState): OnboardingStep? =
    OnboardingStep.entries.firstOrNull { validateOnboardingStep(form, it).isNotEmpty() }
