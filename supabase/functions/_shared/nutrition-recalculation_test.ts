
import {
  shouldRecalculateNutrition,
} from "./nutrition-recalculation.ts";

const automaticGoal = {
  nutrition_goal_mode: "automatic" as const,
  goal_type: "maintain_weight",
  activity_level: "moderate",
  calculation_weight_kg: 80,
};

const inputs = {
  goalType: "maintain_weight",
  activityLevel: "moderate",
  weightKg: 80,
};

Deno.test("No recalcula una meta manual", () => {
  const result = shouldRecalculateNutrition(
    {
      nutrition_goal_mode: "manual",
      goal_type: "maintain_weight",
      activity_level: null,
      calculation_weight_kg: null,
    },
    { ...inputs, weightKg: 85 },
  );

  if (result !== false) {
    throw new Error("Una meta manual no debe recalcularse.");
  }
});

Deno.test("Recalcula si cambia el peso", () => {
  const result = shouldRecalculateNutrition(
    automaticGoal,
    { ...inputs, weightKg: 82 },
  );

  if (result !== true) {
    throw new Error("Debió detectar el cambio de peso.");
  }
});

Deno.test("Recalcula si cambia la actividad", () => {
  const result = shouldRecalculateNutrition(
    automaticGoal,
    { ...inputs, activityLevel: "high" },
  );

  if (result !== true) {
    throw new Error("Debió detectar el cambio de actividad.");
  }
});

Deno.test("Recalcula si cambia el objetivo", () => {
  const result = shouldRecalculateNutrition(
    automaticGoal,
    { ...inputs, goalType: "lose_weight" },
  );

  if (result !== true) {
    throw new Error("Debió detectar el cambio de objetivo.");
  }
});

Deno.test("No recalcula si los datos son iguales", () => {
  const result = shouldRecalculateNutrition(
    automaticGoal,
    inputs,
  );

  if (result !== false) {
    throw new Error("No debería recalcular sin cambios.");
  }
});

Deno.test("No recalcula si todavía no existe una meta", () => {
  const result = shouldRecalculateNutrition(null, inputs);

  if (result !== false) {
    throw new Error("No debería recalcular sin meta previa.");
  }
});
