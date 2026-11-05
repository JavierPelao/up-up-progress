export type SexForFormula = "male" | "female";

export interface RestingEnergyInput {
  weightKg: number;
  heightCm: number;
  ageYears: number;
  sexForFormula: SexForFormula;
}

export function calculateRestingEnergy(
  input: RestingEnergyInput,
): number {
  const {
    weightKg,
    heightCm,
    ageYears,
    sexForFormula,
  } = input;

  if (!Number.isFinite(weightKg) || weightKg <= 0) {
    throw new Error("El peso debe ser mayor que 0.");
  }

  if (
    !Number.isFinite(heightCm) ||
    heightCm < 100 ||
    heightCm > 250
  ) {
    throw new Error("La estatura debe estar entre 100 y 250 cm.");
  }

  if (
    !Number.isInteger(ageYears) ||
    ageYears < 18 ||
    ageYears > 120
  ) {
    throw new Error("La edad debe estar entre 18 y 120 años.");
  }

  if (sexForFormula !== "male" && sexForFormula !== "female") {
    throw new Error("Variante de fórmula no válida.");
  }

  const sexConstant = sexForFormula === "male" ? 5 : -161;

  return (
    10 * weightKg +
    6.25 * heightCm -
    5 * ageYears +
    sexConstant
  );
}


export type ActivityLevel =
  | "sedentary"
  | "light"
  | "moderate"
  | "high"
  | "very_high";

const ACTIVITY_FACTORS: Record<ActivityLevel, number> = {
  sedentary: 1.2,
  light: 1.375,
  moderate: 1.55,
  high: 1.725,
  very_high: 1.9,
};

export function calculateDailyEnergy(
  restingEnergyKcal: number,
  activityLevel: ActivityLevel,
): number {
  if (
    !Number.isFinite(restingEnergyKcal) ||
    restingEnergyKcal <= 0
  ) {
    throw new Error("La energía en reposo debe ser mayor que 0.");
  }

  const factor = ACTIVITY_FACTORS[activityLevel];

  if (factor === undefined) {
    throw new Error("Nivel de actividad no válido.");
  }

  return restingEnergyKcal * factor;
}

export type PhysicalGoal =
  | "gain_muscle"
  | "lose_weight"
  | "maintain_weight"
  | "body_recomposition";

const CALORIE_ADJUSTMENTS: Record<PhysicalGoal, number> = {
  gain_muscle: 10,
  lose_weight: -10,
  maintain_weight: 0,
  body_recomposition: 0,
};

export interface CalorieTargetResult {
  dailyCalorieTarget: number;
  calorieAdjustmentPct: number;
}

export function calculateCalorieTarget(
  dailyEnergyKcal: number,
  goalType: PhysicalGoal,
): CalorieTargetResult {
  if (
    !Number.isFinite(dailyEnergyKcal) ||
    dailyEnergyKcal <= 0
  ) {
    throw new Error("El gasto energético diario debe ser mayor que 0.");
  }

  const adjustmentPct = CALORIE_ADJUSTMENTS[goalType];

  if (adjustmentPct === undefined) {
    throw new Error("Objetivo físico no válido.");
  }

  const adjustedCalories =
    dailyEnergyKcal * (1 + adjustmentPct / 100);

  return {
    dailyCalorieTarget: Math.round(adjustedCalories),
    calorieAdjustmentPct: adjustmentPct,
  };
}


export interface MacroTargetsResult {
  dailyProteinTargetG: number;
  dailyCarbsTargetG: number;
  dailyFatTargetG: number;
}

export function calculateMacroTargets(
  dailyCalorieTarget: number,
  weightKg: number,
): MacroTargetsResult {
  if (
    !Number.isInteger(dailyCalorieTarget) ||
    dailyCalorieTarget <= 0
  ) {
    throw new Error("La meta calórica debe ser un entero positivo.");
  }

  if (!Number.isFinite(weightKg) || weightKg <= 0) {
    throw new Error("El peso debe ser mayor que 0.");
  }

  // 1. Proteínas: 1,8 gramos por kg.
  const proteinG = weightKg * 1.8;
  const proteinKcal = proteinG * 4;

  // 2. Grasas: 25% de las calorías objetivo.
  const fatKcal = dailyCalorieTarget * 0.25;
  const fatG = fatKcal / 9;

  // 3. Carbohidratos: calorías restantes.
  const carbsKcal =
    dailyCalorieTarget - proteinKcal - fatKcal;

  if (carbsKcal < 0) {
    throw new Error(
      "La meta calórica es insuficiente para estas proporciones.",
    );
  }

  const carbsG = carbsKcal / 4;

  // Redondear a un decimal para guardar en NUMERIC(7,1).
  const round1 = (value: number): number =>
    Math.round(value * 10) / 10;

  return {
    dailyProteinTargetG: round1(proteinG),
    dailyCarbsTargetG: round1(carbsG),
    dailyFatTargetG: round1(fatG),
  };
}


export interface AutomaticNutritionInput
  extends RestingEnergyInput {
  activityLevel: ActivityLevel;
  goalType: PhysicalGoal;
}

export interface AutomaticNutritionResult
  extends MacroTargetsResult {
  estimatedTdeeKcal: number;
  dailyCalorieTarget: number;
  calorieAdjustmentPct: number;
  calculationWeightKg: number;
}

export function calculateAutomaticNutritionGoals(
  input: AutomaticNutritionInput,
): AutomaticNutritionResult {
  // 1. Calcular energía en reposo.
  const restingEnergy = calculateRestingEnergy(input);

  // 2. Aplicar el factor de actividad.
  const dailyEnergy = calculateDailyEnergy(
    restingEnergy,
    input.activityLevel,
  );

  // 3. Ajustar calorías según el objetivo.
  const calorieResult = calculateCalorieTarget(
    dailyEnergy,
    input.goalType,
  );

  // 4. Calcular macronutrientes.
  const macroResult = calculateMacroTargets(
    calorieResult.dailyCalorieTarget,
    input.weightKg,
  );

  // 5. Reunir los resultados.
  return {
    ...macroResult,
    ...calorieResult,
    estimatedTdeeKcal: Math.round(dailyEnergy * 10) / 10,
    calculationWeightKg: input.weightKg,
  };
}
