
import { isActivityLevel, isPhysicalGoal, type ActivityLevel, type PhysicalGoal } from "./nutrition-calculator.ts";

export interface ManualGoalsInput {
  goalType: PhysicalGoal;
  activityLevel: ActivityLevel;
  dailyCalorieTarget: number;
  dailyProteinTargetG: number;
  dailyCarbsTargetG: number;
  dailyFatTargetG: number;
}

function validateGrams(value: unknown, field: string): number {
  if (
    typeof value !== "number" ||
    !Number.isFinite(value) ||
    value < 0 ||
    value > 5000
  ) {
    throw new Error(`${field} debe ser un número no negativo.`);
  }

  // Rechazar precisión extra antes de escribir en NUMERIC(8,2): no redondear en silencio.
  if (!/^\d+(?:\.\d{1,2})?$/.test(String(value))) {
    throw new Error(`${field} admite máximo dos decimales.`);
  }

  return value;
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

  if (!isPhysicalGoal(data.goalType)) {
    throw new Error("Objetivo físico no válido.");
  }
  if (!isActivityLevel(data.activityLevel)) {
    throw new Error("Nivel de actividad no válido.");
  }

  if (
    typeof data.dailyCalorieTarget !== "number" ||
    !Number.isInteger(data.dailyCalorieTarget) ||
    data.dailyCalorieTarget <= 0 ||
    data.dailyCalorieTarget > 20000
  ) {
    throw new Error("La meta calórica debe ser un entero positivo.");
  }

  return {
    goalType: data.goalType,
    activityLevel: data.activityLevel,
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
