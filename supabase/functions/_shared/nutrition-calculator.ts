export const ACTIVITY_LEVELS = ["light", "high", "very_high"] as const;
export const PHYSICAL_GOALS = ["gain_muscle", "lose_weight", "maintain_weight"] as const;

export type ActivityLevel = typeof ACTIVITY_LEVELS[number];
export type PhysicalGoal = typeof PHYSICAL_GOALS[number];
type SexForFormula = "male" | "female";

export function isActivityLevel(value: unknown): value is ActivityLevel {
  return typeof value === "string" && ACTIVITY_LEVELS.includes(value as ActivityLevel);
}

export function isPhysicalGoal(value: unknown): value is PhysicalGoal {
  return typeof value === "string" && PHYSICAL_GOALS.includes(value as PhysicalGoal);
}

export interface AutomaticNutritionInput {
  weightKg: number;
  heightCm: number;
  ageYears: number;
  sexForFormula: SexForFormula;
  activityLevel: ActivityLevel;
  goalType: PhysicalGoal;
}

export interface AutomaticNutritionResult {
  bmrKcal: number;
  estimatedTdeeKcal: number;
  dailyCalorieTarget: number;
  calorieAdjustmentPct: number;
  calculationWeightKg: number;
  dailyProteinTargetG: number;
  dailyCarbsTargetG: number;
  dailyFatTargetG: number;
}

export type NutritionIssue =
  | "AGE_OUT_OF_RANGE" | "HEIGHT_OUT_OF_RANGE" | "WEIGHT_OUT_OF_RANGE"
  | "INVALID_SEX" | "INVALID_ACTIVITY" | "INVALID_GOAL" | "NON_POSITIVE_BMR"
  | "CALORIES_OUT_OF_RANGE" | "MACROS_EXCEED_CALORIE_TARGET" | "INCONSISTENT_MACRO_ENERGY";

export class NutritionCalculationError extends Error {
  constructor(public readonly code: NutritionIssue, message: string) {
    super(message);
    this.name = "NutritionCalculationError";
  }
}

// Los factores se expresan en milésimas y la proteína en décimas de g/kg.
// Coinciden con OnboardingNutritionConfig de Android.
const ACTIVITY_FACTORS: Record<ActivityLevel, bigint> = {
  light: 1375n, high: 1500n, very_high: 1900n,
};
const CALORIE_ADJUSTMENTS: Record<PhysicalGoal, number> = {
  gain_muscle: 10, lose_weight: -15, maintain_weight: 0,
};
const PROTEIN_FACTORS: Record<ActivityLevel, Record<PhysicalGoal, bigint>> = {
  light: { maintain_weight: 14n, lose_weight: 16n, gain_muscle: 16n },
  high: { maintain_weight: 18n, lose_weight: 20n, gain_muscle: 18n },
  very_high: { maintain_weight: 20n, lose_weight: 22n, gain_muscle: 20n },
};

function hasDecimalPlaces(value: number, places: number): boolean {
  return new RegExp("^\\d+(?:\\.\\d{1," + places + "})?$").test(String(value));
}

function toFixedInteger(value: number, places: number): bigint {
  const [whole, fraction = ""] = String(value).split(".");
  return BigInt(whole) * 10n ** BigInt(places) + BigInt(fraction.padEnd(places, "0"));
}

// HALF_UP para cantidades no negativas; evita los errores binarios de Math.round(x * 100).
function roundPositive(numerator: bigint, denominator: bigint): bigint {
  return (numerator * 2n + denominator) / (denominator * 2n);
}

/** Cálculo definitivo, sin acceso a red ni base de datos. Los registros se guardan en el handler. */
export function calculateAutomaticNutritionGoals(input: AutomaticNutritionInput): AutomaticNutritionResult {
  if (!Number.isInteger(input.ageYears) || input.ageYears < 19 || input.ageYears > 78) {
    throw new NutritionCalculationError("AGE_OUT_OF_RANGE", "El cálculo automático requiere una edad entre 19 y 78 años.");
  }
  if (!Number.isFinite(input.heightCm) || input.heightCm < 100 || input.heightCm > 250 ||
      !hasDecimalPlaces(input.heightCm, 1)) {
    throw new NutritionCalculationError("HEIGHT_OUT_OF_RANGE", "La estatura debe estar entre 100 y 250 cm, con hasta un decimal.");
  }
  if (!Number.isFinite(input.weightKg) || input.weightKg < 30 || input.weightKg > 300 ||
      !hasDecimalPlaces(input.weightKg, 2)) {
    throw new NutritionCalculationError("WEIGHT_OUT_OF_RANGE", "El cálculo automático requiere entre 30 y 300 kg, con hasta dos decimales.");
  }
  if (input.sexForFormula !== "male" && input.sexForFormula !== "female") {
    throw new NutritionCalculationError("INVALID_SEX", "Variante de fórmula no válida.");
  }
  if (!isActivityLevel(input.activityLevel)) {
    throw new NutritionCalculationError("INVALID_ACTIVITY", "Nivel de actividad no válido.");
  }
  if (!isPhysicalGoal(input.goalType)) {
    throw new NutritionCalculationError("INVALID_GOAL", "Objetivo físico no válido.");
  }

  const weightHundredths = toFixedInteger(input.weightKg, 2);
  const heightTenths = toFixedInteger(input.heightCm, 1);
  // Mifflin–St Jeor en milésimas de kcal. Se preserva la décima de cm admitida por PostgreSQL.
  const bmrThousandths = weightHundredths * 100n + heightTenths * 625n -
    BigInt(input.ageYears) * 5000n + (input.sexForFormula === "male" ? 5000n : -161000n);
  if (bmrThousandths <= 0n) {
    throw new NutritionCalculationError("NON_POSITIVE_BMR", "No se obtuvo una energía basal positiva.");
  }

  // Milésimas de BMR × milésimas del factor = millonésimas de TDEE.
  const tdeeMillionths = bmrThousandths * ACTIVITY_FACTORS[input.activityLevel];
  const adjustment = CALORIE_ADJUSTMENTS[input.goalType];
  const target = roundPositive(tdeeMillionths * BigInt(100 + adjustment), 100000000n);
  if (target < 1500n || target > 5000n) {
    throw new NutritionCalculationError("CALORIES_OUT_OF_RANGE", "La estimación queda fuera del rango automático de 1500 a 5000 kcal. Puedes elegir Manual.");
  }

  // La proteína se redondea primero a centésimas, igual que en Kotlin.
  const proteinHundredths = roundPositive(weightHundredths * PROTEIN_FACTORS[input.activityLevel][input.goalType], 10n);
  const fatEnergyHundredths = target * 30n;
  const carbsEnergyHundredths = target * 100n - proteinHundredths * 4n - fatEnergyHundredths;
  if (carbsEnergyHundredths < 0n) {
    throw new NutritionCalculationError("MACROS_EXCEED_CALORIE_TARGET", "Las proteínas y grasas superan las calorías estimadas.");
  }
  const carbsHundredths = roundPositive(carbsEnergyHundredths, 4n);
  const fatHundredths = roundPositive(fatEnergyHundredths, 9n);
  const errorHundredths = proteinHundredths * 4n + carbsHundredths * 4n + fatHundredths * 9n - target * 100n;
  if (errorHundredths < -9n || errorHundredths > 9n) {
    throw new NutritionCalculationError("INCONSISTENT_MACRO_ENERGY", "No se pudo obtener un reparto energético consistente.");
  }

  return {
    bmrKcal: Number(bmrThousandths) / 1000,
    estimatedTdeeKcal: Number(tdeeMillionths) / 1000000,
    dailyCalorieTarget: Number(target),
    calorieAdjustmentPct: adjustment,
    calculationWeightKg: input.weightKg,
    dailyProteinTargetG: Number(proteinHundredths) / 100,
    dailyCarbsTargetG: Number(carbsHundredths) / 100,
    dailyFatTargetG: Number(fatHundredths) / 100,
  };
}
