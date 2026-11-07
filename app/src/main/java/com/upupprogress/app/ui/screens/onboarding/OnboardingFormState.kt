package com.upupprogress.app.ui.screens.onboarding

/** Límites técnicos provisionales de la interfaz; no son reglas médicas ni del cálculo nutricional. */
object OnboardingInputLimits {
    const val MIN_AGE_YEARS = 18
    const val MAX_AGE_YEARS = 120
    const val MIN_HEIGHT_CM = 100
    const val MAX_HEIGHT_CM = 250
    const val MIN_WEIGHT_HUNDREDTHS_KG = 1
    // NUMERIC(5,2) y weight_kg > 0 en la migración local de body_weight_logs.
    const val MAX_WEIGHT_HUNDREDTHS_KG = 99_999
}

// Opciones locales de presentación. No definen valores de enumeraciones de Supabase.
enum class FormulaSexUi(val label: String) {
    MALE("Masculino"),
    FEMALE("Femenino")
}

enum class ActivityLevelUi(val label: String) {
    SEDENTARY("Sedentario"),
    LIGHTLY_ACTIVE("Ligeramente activo"),
    ACTIVE("Activo"),
    VERY_ACTIVE("Muy activo")
}

enum class PersonalGoalUi(val label: String) {
    GAIN_MUSCLE_OR_WEIGHT("Ganar peso"),
    LOSE_WEIGHT("Perder peso"),
    MAINTAIN_WEIGHT("Mantener peso")
}

enum class OnboardingField {
    NAME, FORMULA_SEX, AGE, HEIGHT, WEIGHT, ACTIVITY, GOAL,
    NUTRITION_MODE, DAILY_CALORIES, PROTEIN, CARBS, FAT, AUTOMATIC_TARGETS
}

/** Borrador del formulario. No representa un perfil guardado ni un onboarding completado. */
data class OnboardingFormState(
    val name: String = "",
    val sexForFormula: FormulaSexUi? = null,
    val ageYears: Int? = null,
    val heightCm: Int? = null,
    // Centésimas de kg para conservar exactamente dos decimales sin errores de coma flotante.
    val weightHundredthsKg: Int? = null,
    val activityLevel: ActivityLevelUi? = null,
    val personalGoal: PersonalGoalUi? = null,
    val nutritionGoalMode: NutritionGoalModeUi? = null,
    val manualNutritionDraft: ManualNutritionDraft = ManualNutritionDraft()
)

fun validateOnboardingForm(form: OnboardingFormState): Map<OnboardingField, String> = buildMap {
    if (form.name.isBlank()) {
        put(OnboardingField.NAME, "Ingresa tu nombre.")
    }
    if (form.sexForFormula == null) {
        put(OnboardingField.FORMULA_SEX, "Selecciona una opción para la fórmula.")
    }
    if (form.ageYears == null) {
        put(OnboardingField.AGE, "Selecciona tu edad.")
    } else if (form.ageYears !in OnboardingInputLimits.MIN_AGE_YEARS..OnboardingInputLimits.MAX_AGE_YEARS) {
        put(OnboardingField.AGE, "Selecciona una edad entre ${OnboardingInputLimits.MIN_AGE_YEARS} y ${OnboardingInputLimits.MAX_AGE_YEARS} años.")
    }
    if (form.heightCm == null) {
        put(OnboardingField.HEIGHT, "Selecciona tu estatura.")
    } else if (form.heightCm !in OnboardingInputLimits.MIN_HEIGHT_CM..OnboardingInputLimits.MAX_HEIGHT_CM) {
        put(OnboardingField.HEIGHT, "Selecciona una estatura entre ${OnboardingInputLimits.MIN_HEIGHT_CM} y ${OnboardingInputLimits.MAX_HEIGHT_CM} cm.")
    }
    if (form.weightHundredthsKg == null) {
        put(OnboardingField.WEIGHT, "Selecciona tu peso.")
    } else if (form.weightHundredthsKg !in OnboardingInputLimits.MIN_WEIGHT_HUNDREDTHS_KG..OnboardingInputLimits.MAX_WEIGHT_HUNDREDTHS_KG) {
        put(OnboardingField.WEIGHT, "Selecciona un peso mayor que cero y de hasta 999,99 kg.")
    }
    if (form.activityLevel == null) {
        put(OnboardingField.ACTIVITY, "Selecciona tu actividad física.")
    }
    if (form.personalGoal == null) {
        put(OnboardingField.GOAL, "Selecciona tu objetivo personal.")
    }
    when (form.nutritionGoalMode) {
        null -> put(OnboardingField.NUTRITION_MODE, "Selecciona cómo definir tus metas nutricionales.")
        NutritionGoalModeUi.AUTOMATIC -> {
            val result = form.automaticNutritionResultOrNull()
            if (result is AutomaticNutritionResult.Unavailable) {
                put(result.issue.toOnboardingField(), automaticNutritionIssueMessage(result.issue))
            }
        }
        NutritionGoalModeUi.MANUAL -> validateManualNutritionDraft(form.manualNutritionDraft).forEach { (field, message) ->
            put(field.toOnboardingField(), message)
        }
    }
}

internal fun ManualNutritionField.toOnboardingField(): OnboardingField = when (this) {
    ManualNutritionField.CALORIES -> OnboardingField.DAILY_CALORIES
    ManualNutritionField.PROTEIN -> OnboardingField.PROTEIN
    ManualNutritionField.CARBS -> OnboardingField.CARBS
    ManualNutritionField.FAT -> OnboardingField.FAT
}

/** Cambiar de modalidad conserva el borrador; Manual requiere las cuatro metas. */
fun OnboardingFormState.manualNutritionTargetsOrNull(): ManualNutritionTargets? =
    if (nutritionGoalMode == NutritionGoalModeUi.MANUAL) {
        parseManualNutritionTargets(manualNutritionDraft)
    } else null

/** Se deriva del borrador actual; no guarda ni reutiliza una estimación anterior. */
internal fun OnboardingFormState.automaticNutritionResultOrNull(): AutomaticNutritionResult? {
    if (nutritionGoalMode != NutritionGoalModeUi.AUTOMATIC) return null
    val input = AutomaticNutritionInput(
        sexForFormula = sexForFormula ?: return null,
        ageYears = ageYears ?: return null,
        heightCm = heightCm ?: return null,
        weightHundredthsKg = weightHundredthsKg ?: return null,
        activityLevel = activityLevel ?: return null,
        personalGoal = personalGoal ?: return null,
    )
    return AutomaticNutritionCalculator(OnboardingNutritionConfig.automaticRules).calculate(input)
}

internal fun AutomaticNutritionIssue.toOnboardingField(): OnboardingField = when (this) {
    AutomaticNutritionIssue.AGE_OUT_OF_RANGE -> OnboardingField.AGE
    AutomaticNutritionIssue.HEIGHT_OUT_OF_RANGE -> OnboardingField.HEIGHT
    AutomaticNutritionIssue.WEIGHT_OUT_OF_RANGE -> OnboardingField.WEIGHT
    AutomaticNutritionIssue.NON_POSITIVE_BMR,
    AutomaticNutritionIssue.CALORIES_OUT_OF_RANGE,
    AutomaticNutritionIssue.MACROS_EXCEED_CALORIE_TARGET,
    AutomaticNutritionIssue.INCONSISTENT_MACRO_ENERGY -> OnboardingField.AUTOMATIC_TARGETS
}

/** Los mensajes consumen la misma configuración que el cálculo, incluidos sus límites. */
internal fun automaticNutritionIssueMessage(issue: AutomaticNutritionIssue): String {
    val rules = OnboardingNutritionConfig.automaticRules
    return when (issue) {
        AutomaticNutritionIssue.AGE_OUT_OF_RANGE ->
            "Para el cálculo automático, la edad debe estar entre ${rules.ageYearsRange.first} y ${rules.ageYearsRange.last} años. Puedes elegir Manual."
        AutomaticNutritionIssue.HEIGHT_OUT_OF_RANGE ->
            "Para el cálculo automático, la estatura debe estar entre ${rules.heightCmRange.first} y ${rules.heightCmRange.last} cm. Puedes elegir Manual."
        AutomaticNutritionIssue.WEIGHT_OUT_OF_RANGE ->
            "Para el cálculo automático, el peso debe estar entre ${formatAutomaticWeightLimit(rules.weightHundredthsKgRange.first)} y ${formatAutomaticWeightLimit(rules.weightHundredthsKgRange.last)} kg. Puedes elegir Manual."
        AutomaticNutritionIssue.CALORIES_OUT_OF_RANGE ->
            "La estimación queda fuera del rango automático de ${rules.dailyCaloriesRange.first} a ${rules.dailyCaloriesRange.last} kcal. Revisa tus datos o elige Manual."
        AutomaticNutritionIssue.MACROS_EXCEED_CALORIE_TARGET ->
            "Las metas de proteínas y grasas superan las calorías estimadas. Revisa tus datos o elige Manual."
        AutomaticNutritionIssue.NON_POSITIVE_BMR,
        AutomaticNutritionIssue.INCONSISTENT_MACRO_ENERGY ->
            "No se pudo obtener una estimación válida. Revisa tus datos o elige Manual."
    }
}

private fun formatAutomaticWeightLimit(hundredthsKg: Int): String =
    hundredthsKg.toBigDecimal().movePointLeft(2).stripTrailingZeros().toPlainString().replace('.', ',')

// Valores simples compatibles con rememberSaveable. No representan persistencia en Supabase.
internal fun OnboardingFormState.toSavedValues(): List<Any> = listOf(
    name,
    sexForFormula?.name.orEmpty(),
    ageYears ?: -1,
    heightCm ?: -1,
    weightHundredthsKg ?: -1,
    activityLevel?.name.orEmpty(),
    personalGoal?.name.orEmpty(),
    nutritionGoalMode?.name.orEmpty(),
    "", // Posición reservada para restaurar el formato anterior del borrador.
    manualNutritionDraft.calories,
    manualNutritionDraft.proteinGrams,
    manualNutritionDraft.carbsGrams,
    manualNutritionDraft.fatGrams
)

internal fun restoreOnboardingForm(saved: List<Any>): OnboardingFormState = OnboardingFormState(
    name = saved.getOrNull(0) as? String ?: "",
    sexForFormula = FormulaSexUi.entries.firstOrNull { it.name == saved.getOrNull(1) },
    ageYears = (saved.getOrNull(2) as? Int)?.takeIf { it >= 0 },
    heightCm = (saved.getOrNull(3) as? Int)?.takeIf { it >= 0 },
    weightHundredthsKg = (saved.getOrNull(4) as? Int)?.takeIf { it >= 0 },
    activityLevel = ActivityLevelUi.entries.firstOrNull { it.name == saved.getOrNull(5) },
    personalGoal = PersonalGoalUi.entries.firstOrNull { it.name == saved.getOrNull(6) },
    nutritionGoalMode = NutritionGoalModeUi.entries.firstOrNull { it.name == saved.getOrNull(7) },
    manualNutritionDraft = ManualNutritionDraft(
        calories = saved.getOrNull(9) as? String ?: "",
        proteinGrams = saved.getOrNull(10) as? String ?: "",
        carbsGrams = saved.getOrNull(11) as? String ?: "",
        fatGrams = saved.getOrNull(12) as? String ?: ""
    )
)

