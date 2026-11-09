import { strict as assert } from "node:assert";
import { validateManualGoals } from "../functions/_shared/manual-goals.ts";

const valid = {
  goalType: "gain_muscle",
  activityLevel: "high",
  dailyCalorieTarget: 2500,
  dailyProteinTargetG: 123.45,
  dailyCarbsTargetG: 250.01,
  dailyFatTargetG: 70.99,
};

Deno.test("manual conserva dos decimales y valores cero explícitos", () => {
  assert.deepEqual(validateManualGoals(valid), valid);
  assert.equal(validateManualGoals({ ...valid, dailyProteinTargetG: 0 }).dailyProteinTargetG, 0);
});

Deno.test("manual rechaza precisión extra en cada macro, sin redondearla", () => {
  for (const field of ["dailyProteinTargetG", "dailyCarbsTargetG", "dailyFatTargetG"]) {
    assert.throws(() => validateManualGoals({ ...valid, [field]: 100.251 }), /dos decimales/);
    assert.throws(() => validateManualGoals({ ...valid, [field]: 0.000000001 }));
  }
});

Deno.test("manual usa límites Kotlin en cada campo", () => {
  for (const value of [1, 20000]) {
    assert.equal(validateManualGoals({ ...valid, dailyCalorieTarget: value }).dailyCalorieTarget, value);
  }
  for (const value of [0, 20001, 1.5, NaN, Infinity, "2500", null]) {
    assert.throws(() => validateManualGoals({ ...valid, dailyCalorieTarget: value }));
  }
  for (const field of ["dailyProteinTargetG", "dailyCarbsTargetG", "dailyFatTargetG"]) {
    assert.doesNotThrow(() => validateManualGoals({ ...valid, [field]: 5000 }));
    for (const value of [-1, 5000.01, NaN, Infinity, "123.45", "123,45", null, undefined]) {
      assert.throws(() => validateManualGoals({ ...valid, [field]: value }));
    }
  }
});

Deno.test("manual solo admite los tres objetivos del ERS", () => {
  for (const goalType of ["gain_muscle", "lose_weight", "maintain_weight"]) {
    assert.equal(validateManualGoals({ ...valid, goalType }).goalType, goalType);
  }
  assert.throws(() => validateManualGoals({ ...valid, goalType: "body_recomposition" }));
  assert.throws(() => validateManualGoals({ ...valid, goalType: "GAIN_MUSCLE_OR_WEIGHT" }));
});

Deno.test("manual conserva la actividad y rechaza opciones retiradas", () => {
  for (const activityLevel of ["light", "high", "very_high"]) {
    assert.equal(validateManualGoals({ ...valid, activityLevel }).activityLevel, activityLevel);
  }
  for (const activityLevel of ["sedentary", "moderate", "ACTIVE", null, undefined]) {
    assert.throws(() => validateManualGoals({ ...valid, activityLevel }));
  }
});

Deno.test("manual exige un objeto con las cuatro metas", () => {
  for (const value of [null, [], 1, "datos", {}]) assert.throws(() => validateManualGoals(value));
  for (const field of ["dailyCalorieTarget", "dailyProteinTargetG", "dailyCarbsTargetG", "dailyFatTargetG"]) {
    assert.throws(() => validateManualGoals({ ...valid, [field]: undefined }));
  }
});
