import { calculateAutomaticNutritionGoals } from "./nutrition-calculator.ts";
import { validateManualGoals } from "./manual-goals.ts";
import { validateWeightLog } from "./weight-log.ts";

/** Solo datos del formulario; el propietario se obtiene del token verificado en el handler. */
export function validateOnboarding(payload: Record<string, unknown>) {
  const name = typeof payload.displayName === "string" ? payload.displayName.trim() : "";
  if (!name) throw new Error("Ingresa tu nombre.");
  const age = payload.ageYears;
  if (typeof age !== "number" || !Number.isInteger(age) || age < 19 || age > 120) {
    throw new Error("Selecciona una edad entre 19 y 120 años.");
  }
  const height = payload.heightCm;
  if (typeof height !== "number" || !Number.isFinite(height) || height < 100 || height > 250 ||
      !/^\d+(?:\.\d)?$/.test(String(height))) {
    throw new Error("Selecciona una estatura entre 100 y 250 cm.");
  }
  const sex = payload.sexForFormula;
  if (sex !== "male" && sex !== "female") throw new Error("Selecciona una opción para la fórmula.");
  const weight = validateWeightLog(payload);
  const mode = payload.nutritionGoalMode;
  if (mode !== "manual" && mode !== "automatic") throw new Error("Selecciona la modalidad nutricional.");
  // Se validan las mismas opciones para ambos modos. Automático ignora metas enviadas por el cliente.
  const goal = mode === "manual" ? validateManualGoals(payload) :
    calculateAutomaticNutritionGoals({
      ageYears: age, heightCm: height, sexForFormula: sex, weightKg: weight.weightKg,
      activityLevel: payload.activityLevel as Parameters<typeof calculateAutomaticNutritionGoals>[0]["activityLevel"],
      goalType: payload.goalType as Parameters<typeof calculateAutomaticNutritionGoals>[0]["goalType"],
    });
  return {
    p_display_name: name, p_age_years: age, p_height_cm: height, p_sex_for_formula: sex,
    p_measured_on: weight.measuredOn, p_weight_kg: weight.weightKg,
    p_goal_type: payload.goalType, p_activity_level: payload.activityLevel, p_nutrition_goal_mode: mode,
    p_daily_calorie_target: goal.dailyCalorieTarget, p_daily_protein_target_g: goal.dailyProteinTargetG,
    p_daily_carbs_target_g: goal.dailyCarbsTargetG, p_daily_fat_target_g: goal.dailyFatTargetG,
    p_calorie_adjustment_pct: "calorieAdjustmentPct" in goal ? goal.calorieAdjustmentPct : null,
    p_calculation_weight_kg: "calculationWeightKg" in goal ? goal.calculationWeightKg : null,
    p_estimated_tdee_kcal: "estimatedTdeeKcal" in goal ? goal.estimatedTdeeKcal : null,
  };
}
