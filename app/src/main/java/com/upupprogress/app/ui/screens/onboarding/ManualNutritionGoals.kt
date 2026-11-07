package com.upupprogress.app.ui.screens.onboarding

import java.math.BigDecimal

// Límites de edición provisionales. No son recomendaciones médicas ni restricciones SQL verificadas.
object ManualNutritionInputLimits {
    const val MIN_CALORIES_KCAL = 1
    const val MAX_CALORIES_KCAL = 20_000
    const val MAX_MACRO_GRAMS = 5_000
    const val MAX_DECIMAL_PLACES = 2
}

// Opciones de presentación; su equivalencia con los valores del backend se verificará aparte.
enum class NutritionGoalModeUi(val label: String) {
    MANUAL("Manual"), AUTOMATIC("Automático")
}

enum class ManualNutritionField(val label: String, val unit: String) {
    CALORIES("Calorías diarias", "kcal"),
    PROTEIN("Proteínas", "g"),
    CARBS("Carbohidratos", "g"),
    FAT("Grasas", "g")
}

/** Texto de edición, separado de las metas válidas para conservar el borrador al cambiar de modalidad. */
data class ManualNutritionDraft(
    val calories: String = "",
    val proteinGrams: String = "",
    val carbsGrams: String = "",
    val fatGrams: String = ""
) {
    operator fun get(field: ManualNutritionField): String = when (field) {
        ManualNutritionField.CALORIES -> calories
        ManualNutritionField.PROTEIN -> proteinGrams
        ManualNutritionField.CARBS -> carbsGrams
        ManualNutritionField.FAT -> fatGrams
    }

    fun withValue(field: ManualNutritionField, value: String): ManualNutritionDraft = when (field) {
        ManualNutritionField.CALORIES -> copy(calories = value)
        ManualNutritionField.PROTEIN -> copy(proteinGrams = value)
        ManualNutritionField.CARBS -> copy(carbsGrams = value)
        ManualNutritionField.FAT -> copy(fatGrams = value)
    }
}

/** Metas locales ya validadas; no es un DTO de Supabase. */
data class ManualNutritionTargets(
    val caloriesKcal: Int,
    val proteinGrams: BigDecimal,
    val carbsGrams: BigDecimal,
    val fatGrams: BigDecimal
)

private val calorieEditPattern =
    Regex("[0-9]{0,${ManualNutritionInputLimits.MAX_CALORIES_KCAL.toString().length}}")
private val macroEditPattern = Regex(
    "[0-9]{0,${ManualNutritionInputLimits.MAX_MACRO_GRAMS.toString().length}}(?:[.,][0-9]{0,${ManualNutritionInputLimits.MAX_DECIMAL_PLACES}})?"
)

/** Admite estados transitorios como vacío o '1,'; la validación final exige un número completo. */
fun acceptsManualNutritionEdit(field: ManualNutritionField, text: String): Boolean =
    if (field == ManualNutritionField.CALORIES) calorieEditPattern.matches(text)
    else macroEditPattern.matches(text)

fun validateManualNutritionDraft(draft: ManualNutritionDraft): Map<ManualNutritionField, String> = buildMap {
    ManualNutritionField.entries.forEach { field ->
        val text = draft[field]
        if (text.isEmpty()) {
            put(field, "Completa esta meta en ${field.unit}.")
        } else if (field == ManualNutritionField.CALORIES) {
            val value = if (acceptsManualNutritionEdit(field, text)) text.toIntOrNull() else null
            if (value == null || value !in ManualNutritionInputLimits.MIN_CALORIES_KCAL..ManualNutritionInputLimits.MAX_CALORIES_KCAL) {
                put(field, "Ingresa un entero entre ${ManualNutritionInputLimits.MIN_CALORIES_KCAL} y ${ManualNutritionInputLimits.MAX_CALORIES_KCAL} kcal.")
            }
        } else {
            val value = if (acceptsManualNutritionEdit(field, text)) text.replace(',', '.').toBigDecimalOrNull() else null
            if (value == null || value < BigDecimal.ZERO || value > ManualNutritionInputLimits.MAX_MACRO_GRAMS.toBigDecimal()) {
                put(field, "Ingresa de 0 a ${ManualNutritionInputLimits.MAX_MACRO_GRAMS} g, con hasta ${ManualNutritionInputLimits.MAX_DECIMAL_PLACES} decimales.")
            }
        }
    }
}

fun parseManualNutritionTargets(draft: ManualNutritionDraft): ManualNutritionTargets? {
    if (validateManualNutritionDraft(draft).isNotEmpty()) return null
    return ManualNutritionTargets(
        caloriesKcal = draft.calories.toInt(),
        proteinGrams = draft.proteinGrams.replace(',', '.').toBigDecimal(),
        carbsGrams = draft.carbsGrams.replace(',', '.').toBigDecimal(),
        fatGrams = draft.fatGrams.replace(',', '.').toBigDecimal()
    )
}
