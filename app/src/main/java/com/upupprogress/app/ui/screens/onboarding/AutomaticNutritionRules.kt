package com.upupprogress.app.ui.screens.onboarding

import java.math.BigDecimal

/** Reglas explícitas del cálculo local. No representan columnas ni enumeraciones de Supabase. */
data class AutomaticNutritionRules(
    val activityFactors: Map<ActivityLevelUi, BigDecimal>,
    val goalAdjustmentPercent: Map<PersonalGoalUi, Int>,
    val proteinGramsPerKg: Map<ActivityLevelUi, Map<PersonalGoalUi, BigDecimal>>,
    val fatEnergyPercent: Int,
    val ageYearsRange: IntRange,
    val heightCmRange: IntRange,
    val weightHundredthsKgRange: IntRange,
    val dailyCaloriesRange: IntRange,
) {
    init {
        require(activityFactors.keys == ActivityLevelUi.entries.toSet()) {
            "Cada nivel de actividad debe tener un factor."
        }
        require(activityFactors.values.all { it > BigDecimal.ZERO }) {
            "Los factores de actividad deben ser positivos."
        }
        require(goalAdjustmentPercent.keys == PersonalGoalUi.entries.toSet()) {
            "Cada objetivo debe tener un ajuste."
        }
        require(goalAdjustmentPercent.getValue(PersonalGoalUi.GAIN_MUSCLE_OR_WEIGHT) > 0)
        require(goalAdjustmentPercent.getValue(PersonalGoalUi.LOSE_WEIGHT) in -99..-1)
        require(goalAdjustmentPercent.getValue(PersonalGoalUi.MAINTAIN_WEIGHT) == 0)
        require(proteinGramsPerKg.keys == ActivityLevelUi.entries.toSet()) {
            "Cada nivel de actividad debe tener una fila de proteínas."
        }
        proteinGramsPerKg.values.forEach { row ->
            require(row.keys == PersonalGoalUi.entries.toSet()) {
                "Cada fila de proteínas debe incluir todos los objetivos."
            }
            require(row.values.all { it > BigDecimal.ZERO }) {
                "Los coeficientes de proteínas en g/kg deben ser positivos."
            }
        }
        require(fatEnergyPercent in 1..99) {
            "El porcentaje de grasas debe estar entre 1 y 99."
        }
        listOf(ageYearsRange, heightCmRange, weightHundredthsKgRange, dailyCaloriesRange).forEach {
            require(!it.isEmpty() && it.first > 0) { "Los límites deben definir un intervalo positivo." }
        }
    }
}

/**
 * Ajustar la multiplicadon de la formula, esto cambiara el resultado de las calorias
 */
object OnboardingNutritionConfig {
    val automaticRules = AutomaticNutritionRules(
        // Multiplicadores del BMR para estimar el gasto diario según la actividad.
        activityFactors = mapOf(
            ActivityLevelUi.SEDENTARY to BigDecimal("1.20"),
            ActivityLevelUi.LIGHTLY_ACTIVE to BigDecimal("1.375"),
            ActivityLevelUi.ACTIVE to BigDecimal("1.50"),
            ActivityLevelUi.VERY_ACTIVE to BigDecimal("1.90"),
        ),
        // Porcentaje sobre el TDEE: positivo para aumentar, negativo para reducir y cero para mantener.
        goalAdjustmentPercent = mapOf(
            PersonalGoalUi.GAIN_MUSCLE_OR_WEIGHT to 10,
            PersonalGoalUi.LOSE_WEIGHT to -15,
            PersonalGoalUi.MAINTAIN_WEIGHT to 0,
        ),
        // Proteína en gramos por kg de peso corporal: tabla del proyecto.
        // Columnas en cada fila: mantener, reducir, ganar masa/peso.
        proteinGramsPerKg = mapOf(
            ActivityLevelUi.SEDENTARY to mapOf(
                PersonalGoalUi.MAINTAIN_WEIGHT to BigDecimal("1.2"),
                PersonalGoalUi.LOSE_WEIGHT to BigDecimal("1.4"),
                PersonalGoalUi.GAIN_MUSCLE_OR_WEIGHT to BigDecimal("1.6"),
            ),
            ActivityLevelUi.LIGHTLY_ACTIVE to mapOf(
                PersonalGoalUi.MAINTAIN_WEIGHT to BigDecimal("1.4"),
                PersonalGoalUi.LOSE_WEIGHT to BigDecimal("1.6"),
                PersonalGoalUi.GAIN_MUSCLE_OR_WEIGHT to BigDecimal("1.6"),
            ),
            ActivityLevelUi.ACTIVE to mapOf(
                PersonalGoalUi.MAINTAIN_WEIGHT to BigDecimal("1.8"),
                PersonalGoalUi.LOSE_WEIGHT to BigDecimal("2.0"),
                PersonalGoalUi.GAIN_MUSCLE_OR_WEIGHT to BigDecimal("1.8"),
            ),
            ActivityLevelUi.VERY_ACTIVE to mapOf(
                PersonalGoalUi.MAINTAIN_WEIGHT to BigDecimal("2.0"),
                PersonalGoalUi.LOSE_WEIGHT to BigDecimal("2.2"),
                PersonalGoalUi.GAIN_MUSCLE_OR_WEIGHT to BigDecimal("2.0"),
            ),
        ),
        // Grasas como porcentaje de calorías. Carbohidratos utilizan la energía restante.
        fatEnergyPercent = 30,
        // Alcance del cálculo automático. Cada intervalo incluye sus dos extremos.
        ageYearsRange = 19..78,
        heightCmRange = 100..250,
        // Centésimas de kg: 3_000 = 30,00 kg y 30_000 = 300,00 kg.
        weightHundredthsKgRange = 3_000..30_000,
        // Meta entera admitida: los resultados fuera de este intervalo se rechazan, sin recortarlos.
        dailyCaloriesRange = 1_500..5_000,
    )
}
