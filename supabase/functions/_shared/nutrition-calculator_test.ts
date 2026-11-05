import {
  calculateRestingEnergy,
  calculateDailyEnergy,
  calculateCalorieTarget,
  calculateMacroTargets,
  calculateAutomaticNutritionGoals,
} from "./nutrition-calculator.ts";

Deno.test("Calcula Mifflin-St Jeor para hombre", () => {
  const result = calculateRestingEnergy({
    weightKg: 80,
    heightCm: 175,
    ageYears: 25,
    sexForFormula: "male",
  });

  if (result !== 1773.75) {
    throw new Error(`Esperado 1773.75, obtenido ${result}`);
  }
});

Deno.test("Calcula Mifflin-St Jeor para mujer", () => {
  const result = calculateRestingEnergy({
    weightKg: 80,
    heightCm: 175,
    ageYears: 25,
    sexForFormula: "female",
  });

  if (result !== 1607.75) {
    throw new Error(`Esperado 1607.75, obtenido ${result}`);
  }
});

Deno.test("Rechaza un peso inválido", () => {
  let errorDetected = false;

  try {
    calculateRestingEnergy({
      weightKg: -5,
      heightCm: 175,
      ageYears: 25,
      sexForFormula: "male",
    });
  } catch {
    errorDetected = true;
  }

  if (!errorDetected) {
    throw new Error("Se esperaba un error de validación.");
  }
});


Deno.test("Calcula gasto diario con actividad moderada", () => {
  const result = calculateDailyEnergy(1773.75, "moderate");

  if (result !== 2749.3125) {
    throw new Error(`Esperado 2749.3125, obtenido ${result}`);
  }
});

Deno.test("Calcula gasto diario con actividad sedentaria", () => {
  const result = calculateDailyEnergy(1773.75, "sedentary");

  if (result !== 2128.5) {
    throw new Error(`Esperado 2128.5, obtenido ${result}`);
  }
});

Deno.test("Rechaza energía en reposo inválida", () => {
  let errorDetected = false;

  try {
    calculateDailyEnergy(-100, "moderate");
  } catch {
    errorDetected = true;
  }

  if (!errorDetected) {
    throw new Error("Se esperaba un error de validación.");
  }
});


Deno.test("Aplica déficit del 10% para reducir peso", () => {
  const result = calculateCalorieTarget(
    2749.3125,
    "lose_weight",
  );

  if (
    result.dailyCalorieTarget !== 2474 ||
    result.calorieAdjustmentPct !== -10
  ) {
    throw new Error("El déficit calórico es incorrecto.");
  }
});

Deno.test("Aplica superávit del 10% para ganar músculo", () => {
  const result = calculateCalorieTarget(
    2749.3125,
    "gain_muscle",
  );

  if (
    result.dailyCalorieTarget !== 3024 ||
    result.calorieAdjustmentPct !== 10
  ) {
    throw new Error("El superávit calórico es incorrecto.");
  }
});

Deno.test("Mantiene calorías para mantener peso", () => {
  const result = calculateCalorieTarget(
    2749.3125,
    "maintain_weight",
  );

  if (
    result.dailyCalorieTarget !== 2749 ||
    result.calorieAdjustmentPct !== 0
  ) {
    throw new Error("El mantenimiento calórico es incorrecto.");
  }
});

Deno.test("Usa mantenimiento inicial para recomposición", () => {
  const result = calculateCalorieTarget(
    2749.3125,
    "body_recomposition",
  );

  if (
    result.dailyCalorieTarget !== 2749 ||
    result.calorieAdjustmentPct !== 0
  ) {
    throw new Error("El cálculo de recomposición es incorrecto.");
  }
});

Deno.test("Rechaza gasto energético inválido", () => {
  let errorDetected = false;

  try {
    calculateCalorieTarget(-100, "lose_weight");
  } catch {
    errorDetected = true;
  }

  if (!errorDetected) {
    throw new Error("Se esperaba un error de validación.");
  }
});


Deno.test("Calcula macros para 2474 kcal y 80 kg", () => {
  const result = calculateMacroTargets(2474, 80);

  if (
    result.dailyProteinTargetG !== 144 ||
    result.dailyFatTargetG !== 68.7 ||
    result.dailyCarbsTargetG !== 319.9
  ) {
    throw new Error("Los macronutrientes son incorrectos.");
  }
});

Deno.test("Calcula macros para 2000 kcal y 70 kg", () => {
  const result = calculateMacroTargets(2000, 70);

  if (
    result.dailyProteinTargetG !== 126 ||
    result.dailyFatTargetG !== 55.6 ||
    result.dailyCarbsTargetG !== 249
  ) {
    throw new Error("Los macronutrientes son incorrectos.");
  }
});

Deno.test("Rechaza peso inválido en macros", () => {
  let errorDetected = false;

  try {
    calculateMacroTargets(2000, 0);
  } catch {
    errorDetected = true;
  }

  if (!errorDetected) {
    throw new Error("Se esperaba un error por peso inválido.");
  }
});

Deno.test("Rechaza metas con carbohidratos negativos", () => {
  let errorDetected = false;

  try {
    calculateMacroTargets(1000, 200);
  } catch {
    errorDetected = true;
  }

  if (!errorDetected) {
    throw new Error("Se esperaba un error por calorías insuficientes.");
  }
});


Deno.test("Calcula metas automáticas completas", () => {
  const result = calculateAutomaticNutritionGoals({
    weightKg: 80,
    heightCm: 175,
    ageYears: 25,
    sexForFormula: "male",
    activityLevel: "moderate",
    goalType: "lose_weight",
  });

  if (
    result.estimatedTdeeKcal !== 2749.3 ||
    result.dailyCalorieTarget !== 2474 ||
    result.calorieAdjustmentPct !== -10 ||
    result.dailyProteinTargetG !== 144 ||
    result.dailyCarbsTargetG !== 319.9 ||
    result.dailyFatTargetG !== 68.7 ||
    result.calculationWeightKg !== 80
  ) {
    throw new Error("Las metas automáticas son incorrectas.");
  }
});

Deno.test("Calcula metas para mujer en mantenimiento", () => {
  const result = calculateAutomaticNutritionGoals({
    weightKg: 60,
    heightCm: 165,
    ageYears: 30,
    sexForFormula: "female",
    activityLevel: "sedentary",
    goalType: "maintain_weight",
  });

  if (
    result.estimatedTdeeKcal !== 1584.3 ||
    result.dailyCalorieTarget !== 1584 ||
    result.dailyProteinTargetG !== 108 ||
    result.dailyCarbsTargetG !== 189 ||
    result.dailyFatTargetG !== 44
  ) {
    throw new Error("Las metas de mantenimiento son incorrectas.");
  }
});

Deno.test("Rechaza datos inválidos en cálculo completo", () => {
  let errorDetected = false;

  try {
    calculateAutomaticNutritionGoals({
      weightKg: -10,
      heightCm: 175,
      ageYears: 25,
      sexForFormula: "male",
      activityLevel: "moderate",
      goalType: "lose_weight",
    });
  } catch {
    errorDetected = true;
  }

  if (!errorDetected) {
    throw new Error("Se esperaba un error de validación.");
  }
});
