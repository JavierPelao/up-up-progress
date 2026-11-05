
export interface CurrentNutritionGoal {
  nutrition_goal_mode: "manual" | "automatic";
  goal_type: string;
  activity_level: string | null;
  calculation_weight_kg: number | null;
}

export interface CurrentNutritionInputs {
  goalType: string;
  activityLevel: string;
  weightKg: number;
}

export function shouldRecalculateNutrition(
  currentGoal: CurrentNutritionGoal | null,
  inputs: CurrentNutritionInputs,
): boolean {
  // Sin meta previa, todavía no existe una configuración
  // automática que debamos recalcular.
  if (!currentGoal) {
    return false;
  }

  // Las metas manuales nunca se recalculan automáticamente.
  if (currentGoal.nutrition_goal_mode === "manual") {
    return false;
  }

  // Detectar cambios en objetivo o actividad.
  if (
    currentGoal.goal_type !== inputs.goalType ||
    currentGoal.activity_level !== inputs.activityLevel
  ) {
    return true;
  }

  // Detectar cambios en el peso utilizado para calcular.
  if (currentGoal.calculation_weight_kg === null) {
    return true;
  }

  return Number(currentGoal.calculation_weight_kg) !== inputs.weightKg;
}
