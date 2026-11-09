import { strict as assert } from "node:assert";
import { validateOnboarding } from "../functions/_shared/onboarding.ts";

const form = {
  displayName: " Ejemplo ", ageYears: 30, heightCm: 175, sexForFormula: "male",
  measuredOn: "2026-11-08", weightKg: 70, activityLevel: "high",
  goalType: "maintain_weight", nutritionGoalMode: "automatic",
};

Deno.test("alta automática calcula en backend e ignora metas y propietario enviados", () => {
  const result = validateOnboarding({ ...form, userId: "otro", dailyCalorieTarget: 9999 });
  assert.equal(result.p_display_name, "Ejemplo");
  assert.equal(result.p_daily_calorie_target, 2473);
  assert.equal(result.p_daily_carbs_target_g, 306.78);
  assert.equal(result.p_estimated_tdee_kcal, 2473.125);
  assert.equal("p_user_id" in result, false);
});

Deno.test("alta manual conserva metas explícitas y no deriva cálculos automáticos", () => {
  const result = validateOnboarding({
    ...form, ageYears: 120, weightKg: 999.99, nutritionGoalMode: "manual",
    dailyCalorieTarget: 2200, dailyProteinTargetG: 150.55,
    dailyCarbsTargetG: 250.25, dailyFatTargetG: 65.01,
  });
  assert.equal(result.p_daily_protein_target_g, 150.55);
  assert.equal(result.p_calorie_adjustment_pct, null);
  assert.equal(result.p_calculation_weight_kg, null);
  assert.equal(result.p_estimated_tdee_kcal, null);
});

Deno.test("alta valida campos físicos, fecha, objetivo y modalidad antes de guardar", () => {
  for (const changes of [
    { displayName: " " }, { ageYears: 18 }, { heightCm: 251 }, { sexForFormula: "otro" },
    { measuredOn: "2026-02-30" }, { weightKg: 70.001 },
    { activityLevel: "sedentary" }, { goalType: "body_recomposition" }, { nutritionGoalMode: "later" },
  ]) assert.throws(() => validateOnboarding({ ...form, ...changes }));
});
