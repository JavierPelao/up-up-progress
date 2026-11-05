
import { validateManualGoals } from "./manual-goals.ts";

Deno.test("Acepta metas nutricionales manuales válidas", () => {
  const result = validateManualGoals({
    goalType: "maintain_weight",
    dailyCalorieTarget: 2400,
    dailyProteinTargetG: 150,
    dailyCarbsTargetG: 300,
    dailyFatTargetG: 66.7,
  });

  if (
    result.goalType !== "maintain_weight" ||
    result.dailyCalorieTarget !== 2400 ||
    result.dailyProteinTargetG !== 150 ||
    result.dailyCarbsTargetG !== 300 ||
    result.dailyFatTargetG !== 66.7
  ) {
    throw new Error("La validación manual es incorrecta.");
  }
});

Deno.test("Rechaza un objetivo físico inválido", () => {
  let errorDetected = false;

  try {
    validateManualGoals({
      goalType: "bulk_fast",
      dailyCalorieTarget: 2400,
      dailyProteinTargetG: 150,
      dailyCarbsTargetG: 300,
      dailyFatTargetG: 66.7,
    });
  } catch {
    errorDetected = true;
  }

  if (!errorDetected) {
    throw new Error("Se esperaba un error.");
  }
});

Deno.test("Rechaza proteínas negativas", () => {
  let errorDetected = false;

  try {
    validateManualGoals({
      goalType: "maintain_weight",
      dailyCalorieTarget: 2400,
      dailyProteinTargetG: -10,
      dailyCarbsTargetG: 300,
      dailyFatTargetG: 66.7,
    });
  } catch {
    errorDetected = true;
  }

  if (!errorDetected) {
    throw new Error("Se esperaba un error.");
  }
});

Deno.test("Rechaza calorías con decimales", () => {
  let errorDetected = false;

  try {
    validateManualGoals({
      goalType: "maintain_weight",
      dailyCalorieTarget: 2400.5,
      dailyProteinTargetG: 150,
      dailyCarbsTargetG: 300,
      dailyFatTargetG: 66.7,
    });
  } catch {
    errorDetected = true;
  }

  if (!errorDetected) {
    throw new Error("Se esperaba un error.");
  }
});

Deno.test("Rechaza macros con más de un decimal", () => {
  let errorDetected = false;

  try {
    validateManualGoals({
      goalType: "maintain_weight",
      dailyCalorieTarget: 2400,
      dailyProteinTargetG: 150.55,
      dailyCarbsTargetG: 300,
      dailyFatTargetG: 66.7,
    });
  } catch {
    errorDetected = true;
  }

  if (!errorDetected) {
    throw new Error("Se esperaba un error.");
  }
});
