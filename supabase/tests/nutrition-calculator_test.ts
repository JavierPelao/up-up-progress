import { strict as assert } from "node:assert";
import {
  calculateAutomaticNutritionGoals, isActivityLevel, isPhysicalGoal,
  NutritionCalculationError, type AutomaticNutritionInput,
} from "../functions/_shared/nutrition-calculator.ts";

const valid: AutomaticNutritionInput = {
  sexForFormula: "male", ageYears: 30, heightCm: 175, weightKg: 70,
  activityLevel: "high", goalType: "maintain_weight",
};

Deno.test("backend y Kotlin cumplen los mismos 810 casos del contrato", async () => {
  const text = await Deno.readTextFile(new URL("../../app/src/test/resources/nutrition-parity.tsv", import.meta.url));
  const lines = text.split(/\r?\n/).filter((line) => line.trim() && !line.startsWith("#"));
  assert.equal(lines.length, 810);
  let estimates = 0;
  for (const line of lines) {
    const v = line.split("\t");
    const activityLevel = v[4], goalType = v[5];
    assert.ok(isActivityLevel(activityLevel));
    assert.ok(isPhysicalGoal(goalType));
    const input: AutomaticNutritionInput = {
      sexForFormula: v[0] as "male" | "female", ageYears: Number(v[1]),
      heightCm: Number(v[2]), weightKg: Number(v[3]), activityLevel, goalType,
    };
    if (v[6] === "UNAVAILABLE") {
      assert.throws(() => calculateAutomaticNutritionGoals(input),
        (error) => error instanceof NutritionCalculationError && error.code === v[7], line);
    } else {
      const result = calculateAutomaticNutritionGoals(input);
      const actual = [result.bmrKcal, result.estimatedTdeeKcal, result.calorieAdjustmentPct,
        result.dailyCalorieTarget, result.dailyProteinTargetG, result.dailyCarbsTargetG, result.dailyFatTargetG];
      assert.deepEqual(actual, v.slice(7).map(Number), line);
      estimates++;
    }
  }
  assert.ok(estimates > 0);
});

Deno.test("HALF_UP redondea las calorías y reserva la proteína ya redondeada", () => {
  const half = calculateAutomaticNutritionGoals({ ...valid, heightCm: 176 });
  assert.equal(half.estimatedTdeeKcal, 2482.5);
  assert.equal(half.dailyCalorieTarget, 2483);
  assert.equal(half.dailyCarbsTargetG, 308.53);
  const roundedProtein = calculateAutomaticNutritionGoals({
    ...valid, weightKg: 70.19, activityLevel: "light", goalType: "lose_weight",
  });
  assert.equal(roundedProtein.dailyCalorieTarget, 1929);
  assert.equal(roundedProtein.dailyProteinTargetG, 112.30);
  assert.equal(roundedProtein.dailyCarbsTargetG, 225.28);
  assert.equal(roundedProtein.dailyFatTargetG, 64.30);
});

Deno.test("conserva TDEE intermedio y la décima de cm existente en PostgreSQL", () => {
  const fractional = calculateAutomaticNutritionGoals({
    ...valid, weightKg: 70.25, activityLevel: "light", goalType: "gain_muscle",
  });
  assert.equal(fractional.estimatedTdeeKcal, 2270.46875);
  assert.equal(fractional.dailyCalorieTarget, 2498);
  const height = calculateAutomaticNutritionGoals({
    ...valid, weightKg: 70.25, heightCm: 175.1, activityLevel: "light",
  });
  assert.equal(height.estimatedTdeeKcal, 2271.328125);
  assert.equal(height.dailyCalorieTarget, 2271);
  assert.equal(height.dailyProteinTargetG, 98.35);
  assert.equal(height.dailyCarbsTargetG, 299.08);
  assert.equal(height.dailyFatTargetG, 75.70);
});

Deno.test("rechaza entradas y opciones retiradas con códigos identificables", () => {
  const cases: Array<[Partial<AutomaticNutritionInput>, string]> = [
    [{ ageYears: 18 }, "AGE_OUT_OF_RANGE"], [{ ageYears: 79 }, "AGE_OUT_OF_RANGE"],
    [{ heightCm: 99 }, "HEIGHT_OUT_OF_RANGE"], [{ heightCm: 250.01 }, "HEIGHT_OUT_OF_RANGE"],
    [{ weightKg: 29.99 }, "WEIGHT_OUT_OF_RANGE"], [{ weightKg: 300.01 }, "WEIGHT_OUT_OF_RANGE"],
    [{ weightKg: 70.001 }, "WEIGHT_OUT_OF_RANGE"], [{ weightKg: NaN }, "WEIGHT_OUT_OF_RANGE"],
  ];
  for (const activityLevel of ["sedentary", "moderate", "ACTIVE", "__proto__"]) {
    cases.push([{ activityLevel: activityLevel as AutomaticNutritionInput["activityLevel"] }, "INVALID_ACTIVITY"]);
  }
  cases.push([{ goalType: "body_recomposition" as AutomaticNutritionInput["goalType"] }, "INVALID_GOAL"]);
  for (const [changes, code] of cases) {
    assert.throws(() => calculateAutomaticNutritionGoals({ ...valid, ...changes }),
      (error) => error instanceof NutritionCalculationError && error.code === code);
  }
});
