package com.upupprogress.app.data.model

import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.serialization.json.*

data class AuthenticatedAccount(val id: String, val email: String?)

data class NutritionTargets(
    val caloriesKcal: Int,
    val proteinGrams: BigDecimal,
    val carbsGrams: BigDecimal,
    val fatGrams: BigDecimal,
)

/** Solo datos del perfil. Las credenciales nunca forman parte de esta petición. */
data class OnboardingSubmission(
    val displayName: String,
    val ageYears: Int,
    val heightCm: Int,
    val sexForFormula: String,
    val weightKg: BigDecimal,
    val measuredOn: LocalDate,
    val activityLevel: String,
    val goalType: String,
    val nutritionGoalMode: String,
    val manualTargets: NutritionTargets? = null,
) {
    fun toRequest(): JsonObject = buildJsonObject {
        put("action", "complete_onboarding")
        put("displayName", displayName)
        put("ageYears", ageYears)
        put("heightCm", heightCm)
        put("sexForFormula", sexForFormula)
        put("weightKg", Json.parseToJsonElement(weightKg.toPlainString()))
        put("measuredOn", measuredOn.toString())
        put("activityLevel", activityLevel)
        put("goalType", goalType)
        put("nutritionGoalMode", nutritionGoalMode)
        if (nutritionGoalMode == "manual") {
            val targets = requireNotNull(manualTargets)
            put("dailyCalorieTarget", targets.caloriesKcal)
            put("dailyProteinTargetG", Json.parseToJsonElement(targets.proteinGrams.toPlainString()))
            put("dailyCarbsTargetG", Json.parseToJsonElement(targets.carbsGrams.toPlainString()))
            put("dailyFatTargetG", Json.parseToJsonElement(targets.fatGrams.toPlainString()))
        }
    }
}

/** Instantánea confirmada por el backend, que puede contener un perfil incompleto. */
data class OnboardingStatus(
    val isComplete: Boolean,
    val userId: String? = null,
    val displayName: String? = null,
    val ageYears: Int? = null,
    val heightCm: BigDecimal? = null,
    val sexForFormula: String? = null,
    val weightKg: BigDecimal? = null,
    val measuredOn: LocalDate? = null,
    val activityLevel: String? = null,
    val goalType: String? = null,
    val nutritionGoalMode: String? = null,
    val targets: NutritionTargets? = null,
) {
    companion object {
        fun fromResponse(response: JsonObject): OnboardingStatus {
            val state = requireNotNull(response["onboarding"] as? JsonObject)
            val profile = state["profile"] as? JsonObject
            val weight = state["weight"] as? JsonObject
            val goal = state["goal"] as? JsonObject
            fun JsonObject?.text(key: String) = (this?.get(key) as? JsonPrimitive)?.contentOrNull
            fun JsonObject?.decimal(key: String) = text(key)?.toBigDecimalOrNull()
            val calories = goal.text("daily_calorie_target")?.toIntOrNull()
            val protein = goal.decimal("daily_protein_target_g")
            val carbs = goal.decimal("daily_carbs_target_g")
            val fat = goal.decimal("daily_fat_target_g")
            return OnboardingStatus(
                isComplete = (state["is_complete"] as? JsonPrimitive)?.booleanOrNull == true,
                userId = profile.text("user_id"),
                displayName = profile.text("display_name"),
                ageYears = profile.text("age_years")?.toIntOrNull(),
                heightCm = profile.decimal("height_cm"),
                sexForFormula = profile.text("sex_for_formula"),
                weightKg = weight.decimal("weight_kg"),
                measuredOn = weight.text("measured_on")?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                activityLevel = goal.text("activity_level"),
                goalType = goal.text("goal_type"),
                nutritionGoalMode = goal.text("nutrition_goal_mode"),
                targets = if (calories != null && protein != null && carbs != null && fat != null)
                    NutritionTargets(calories, protein, carbs, fat) else null,
            )
        }
    }
}
