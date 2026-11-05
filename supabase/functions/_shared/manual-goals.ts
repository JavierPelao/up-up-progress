
import type { PhysicalGoal } from "./nutrition-calculator.ts";

export interface ManualGoalsInput {
  goalType: PhysicalGoal;
  dailyCalorieTarget: number;
  dailyProteinTargetG: number;
  dailyCarbsTargetG: number;
  dailyFatTargetG: number;
}

const VALID_GOALS: readonly string[] = [
  "gain_muscle",
  "lose_weight",
  "maintain_weight",
  "body_recomposition",
];

function validateGrams(value: unknown, field: string): number {
  if (
    typeof value !== "number" ||
    !Number.isFinite(value) ||
    value < 0 ||
    value > 999999.9
  ) {
    throw new Error(`${field} debe ser un número no negativo.`);
  }

  // Las columnas PostgreSQL utilizan NUMERIC(7,1).
  const rounded = Math.round(value * 10) / 10;

  if (Math.abs(value - rounded) > 0.000000001) {
    throw new Error(`${field} admite máximo un decimal.`);
  }

  return rounded;
}

export function validateManualGoals(
  input: unknown,
): ManualGoalsInput {
  if (
    typeof input !== "object" ||
    input === null ||
    Array.isArray(input)
  ) {
    throw new Error("Los datos deben ser un objeto.");
  }

  const data = input as Record<string, unknown>;

  if (
    typeof data.goalType !== "string" ||
    !VALID_GOALS.includes(data.goalType)
  ) {
    throw new Error("Objetivo físico no válido.");
  }

  if (
    typeof data.dailyCalorieTarget !== "number" ||
    !Number.isInteger(data.dailyCalorieTarget) ||
    data.dailyCalorieTarget <= 0 ||
    data.dailyCalorieTarget > 2147483647
  ) {
    throw new Error("La meta calórica debe ser un entero positivo.");
  }

  return {
    goalType: data.goalType as PhysicalGoal,
    dailyCalorieTarget: data.dailyCalorieTarget,
    dailyProteinTargetG: validateGrams(
      data.dailyProteinTargetG,
      "Proteínas",
    ),
    dailyCarbsTargetG: validateGrams(
      data.dailyCarbsTargetG,
      "Carbohidratos",
    ),
    dailyFatTargetG: validateGrams(
      data.dailyFatTargetG,
      "Grasas",
    ),
  };
}
