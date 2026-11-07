package com.upupprogress.app.ui.screens.onboarding

import java.math.BigDecimal
import java.math.RoundingMode

data class AutomaticNutritionInput(
    val sexForFormula: FormulaSexUi,
    val ageYears: Int,
    val heightCm: Int,
    val weightHundredthsKg: Int,
    val activityLevel: ActivityLevelUi,
    val personalGoal: PersonalGoalUi,
)

/** Estimación local orientativa. No acredita persistencia ni finaliza el onboarding. */
data class AutomaticNutritionEstimate(
    val bmrKcal: BigDecimal,
    val estimatedTdeeKcal: BigDecimal,
    val calorieAdjustmentPercent: Int,
    val caloriesKcal: Int,
    val proteinGrams: BigDecimal,
    val carbsGrams: BigDecimal,
    val fatGrams: BigDecimal,
) {
    val caloriesFromMacrosKcal: BigDecimal
        get() = proteinGrams * NutritionMath.PROTEIN_KCAL_PER_GRAM +
            carbsGrams * NutritionMath.CARBS_KCAL_PER_GRAM +
            fatGrams * NutritionMath.FAT_KCAL_PER_GRAM
}

enum class AutomaticNutritionIssue {
    AGE_OUT_OF_RANGE,
    HEIGHT_OUT_OF_RANGE,
    WEIGHT_OUT_OF_RANGE,
    NON_POSITIVE_BMR,
    CALORIES_OUT_OF_RANGE,
    MACROS_EXCEED_CALORIE_TARGET,
    INCONSISTENT_MACRO_ENERGY,
}

sealed interface AutomaticNutritionResult {
    data class Estimated(val estimate: AutomaticNutritionEstimate) : AutomaticNutritionResult
    data class Unavailable(val issue: AutomaticNutritionIssue) : AutomaticNutritionResult
}

/**
 * Kotlin puro, sin Compose, red ni estado de sesión.
 * Requiere reglas explícitas; el formulario utiliza OnboardingNutritionConfig.automaticRules.
 */
class AutomaticNutritionCalculator(private val rules: AutomaticNutritionRules) {

    fun calculate(input: AutomaticNutritionInput): AutomaticNutritionResult {
        if (input.ageYears !in rules.ageYearsRange) {
            return AutomaticNutritionResult.Unavailable(AutomaticNutritionIssue.AGE_OUT_OF_RANGE)
        }
        if (input.heightCm !in rules.heightCmRange) {
            return AutomaticNutritionResult.Unavailable(AutomaticNutritionIssue.HEIGHT_OUT_OF_RANGE)
        }
        if (input.weightHundredthsKg !in rules.weightHundredthsKgRange) {
            return AutomaticNutritionResult.Unavailable(AutomaticNutritionIssue.WEIGHT_OUT_OF_RANGE)
        }

        val weightKg = input.weightHundredthsKg.toBigDecimal().movePointLeft(2)
        val sexOffset = when (input.sexForFormula) {
            FormulaSexUi.MALE -> NutritionMath.MALE_OFFSET
            FormulaSexUi.FEMALE -> NutritionMath.FEMALE_OFFSET
        }
        val bmr = weightKg * NutritionMath.WEIGHT_COEFFICIENT +
            input.heightCm.toBigDecimal() * NutritionMath.HEIGHT_COEFFICIENT -
            input.ageYears.toBigDecimal() * NutritionMath.AGE_COEFFICIENT + sexOffset
        if (bmr <= BigDecimal.ZERO) {
            return AutomaticNutritionResult.Unavailable(AutomaticNutritionIssue.NON_POSITIVE_BMR)
        }

        val tdee = bmr * rules.activityFactors.getValue(input.activityLevel)
        val adjustment = rules.goalAdjustmentPercent.getValue(input.personalGoal)
        val target = (tdee * (BigDecimal.ONE + adjustment.toBigDecimal().movePointLeft(2)))
            .setScale(0, RoundingMode.HALF_UP)
        // Se rechaza el resultado fuera del alcance; no se recorta ni se sustituye por un mínimo.
        if (target < rules.dailyCaloriesRange.first.toBigDecimal() ||
            target > rules.dailyCaloriesRange.last.toBigDecimal()
        ) {
            return AutomaticNutritionResult.Unavailable(AutomaticNutritionIssue.CALORIES_OUT_OF_RANGE)
        }

        val proteinFactor = rules.proteinGramsPerKg.getValue(input.activityLevel)
            .getValue(input.personalGoal)
        val proteinGrams = (weightKg * proteinFactor)
            .setScale(NutritionMath.MACRO_DECIMAL_PLACES, RoundingMode.HALF_UP)
        val fatEnergy = (target * rules.fatEnergyPercent.toBigDecimal()).movePointLeft(2)
        // La proteína ya redondeada se reserva primero; los carbohidratos usan el resto.
        val carbsEnergy = target - proteinGrams * NutritionMath.PROTEIN_KCAL_PER_GRAM - fatEnergy
        if (carbsEnergy < BigDecimal.ZERO) {
            return AutomaticNutritionResult.Unavailable(AutomaticNutritionIssue.MACROS_EXCEED_CALORIE_TARGET)
        }

        fun grams(energyKcal: BigDecimal, kcalPerGram: BigDecimal): BigDecimal =
            energyKcal.divide(kcalPerGram, NutritionMath.MACRO_DECIMAL_PLACES, RoundingMode.HALF_UP)

        val estimate = AutomaticNutritionEstimate(
            bmrKcal = bmr,
            estimatedTdeeKcal = tdee,
            calorieAdjustmentPercent = adjustment,
            caloriesKcal = target.intValueExact(),
            proteinGrams = proteinGrams,
            carbsGrams = grams(carbsEnergy, NutritionMath.CARBS_KCAL_PER_GRAM),
            fatGrams = grams(fatEnergy, NutritionMath.FAT_KCAL_PER_GRAM),
        )
        if ((estimate.caloriesFromMacrosKcal - target).abs() > NutritionMath.MAX_ENERGY_ERROR_KCAL) {
            return AutomaticNutritionResult.Unavailable(AutomaticNutritionIssue.INCONSISTENT_MACRO_ENERGY)
        }
        return AutomaticNutritionResult.Estimated(estimate)
    }
}

private object NutritionMath {
    val WEIGHT_COEFFICIENT = BigDecimal("10")
    val HEIGHT_COEFFICIENT = BigDecimal("6.25")
    val AGE_COEFFICIENT = BigDecimal("5")
    val MALE_OFFSET = BigDecimal("5")
    val FEMALE_OFFSET = BigDecimal("-161")
    val PROTEIN_KCAL_PER_GRAM = BigDecimal("4")
    val CARBS_KCAL_PER_GRAM = BigDecimal("4")
    val FAT_KCAL_PER_GRAM = BigDecimal("9")
    const val MACRO_DECIMAL_PLACES = 2
    // La proteína redondeada ya se reserva en el reparto. El error restante es como máximo
    // 0,005 × (4 + 9) = 0,065 kcal por el redondeo final de carbohidratos y grasas.
    val MAX_ENERGY_ERROR_KCAL = BigDecimal("0.09")
}
