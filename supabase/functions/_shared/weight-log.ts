
export interface WeightLogInput {
  measuredOn: string;
  weightKg: number;
}

export function validateWeightLog(
  payload: Record<string, unknown>,
): WeightLogInput {
  const { measuredOn, weightKg } = payload;

  // Fecha en formato YYYY-MM-DD.
  if (
    typeof measuredOn !== "string" ||
    !/^\d{4}-\d{2}-\d{2}$/.test(measuredOn)
  ) {
    throw new Error("La fecha debe tener formato YYYY-MM-DD.");
  }

  // Impedir fechas inexistentes, como 2026-02-30.
  const parsedDate = new Date(`${measuredOn}T00:00:00Z`);

  if (
    Number.isNaN(parsedDate.getTime()) ||
    parsedDate.toISOString().slice(0, 10) !== measuredOn
  ) {
    throw new Error("La fecha ingresada no es válida.");
  }

  // Coincide con NUMERIC(5,2) de body_weight_logs.
  if (
    typeof weightKg !== "number" ||
    !Number.isFinite(weightKg) ||
    weightKg <= 0 ||
    weightKg > 999.99 ||
    Math.abs(weightKg * 100 - Math.round(weightKg * 100)) > 1e-6
  ) {
    throw new Error(
      "El peso debe ser positivo y tener como máximo dos decimales.",
    );
  }

  return { measuredOn, weightKg };
}
