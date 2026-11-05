
import { validateWeightLog } from "./weight-log.ts";

Deno.test("Acepta un registro de peso válido", () => {
  const result = validateWeightLog({
    measuredOn: "2026-11-04",
    weightKg: 81.25,
  });

  if (
    result.measuredOn !== "2026-11-04" ||
    result.weightKg !== 81.25
  ) {
    throw new Error("El resultado no coincide.");
  }
});

Deno.test("Rechaza una fecha inexistente", () => {
  let rejected = false;

  try {
    validateWeightLog({
      measuredOn: "2026-02-30",
      weightKg: 81,
    });
  } catch {
    rejected = true;
  }

  if (!rejected) throw new Error("Debió rechazar la fecha.");
});

Deno.test("Rechaza un peso negativo", () => {
  let rejected = false;

  try {
    validateWeightLog({
      measuredOn: "2026-11-04",
      weightKg: -5,
    });
  } catch {
    rejected = true;
  }

  if (!rejected) throw new Error("Debió rechazar el peso.");
});

Deno.test("Rechaza más de dos decimales", () => {
  let rejected = false;

  try {
    validateWeightLog({
      measuredOn: "2026-11-04",
      weightKg: 81.123,
    });
  } catch {
    rejected = true;
  }

  if (!rejected) throw new Error("Debió rechazar el peso.");
});
