
import { createClient } from "npm:@supabase/supabase-js@2";

const DAILY_LIMIT = 10;
const HISTORY_DAYS = 30;

type TrainingSet = {
  set_number: number;
  weight_kg: number | string;
  reps: number;
};

type TrainingExercise = {
  exercise_id: string;
  exercises: { name: string } | { name: string }[] | null;
  performed_sets: TrainingSet[];
};

type TrainingSession = {
  id: string;
  performed_on: string;
  workout_exercises: TrainingExercise[];
};

type SessionRecord = {
  exercise_id: string;
  exercise_name: string;
  date: string;
  sets_count: number;
  max_weight_kg: number;
  reps_at_max_weight: number;
};

type ExerciseTrend = {
  exercise: string;
  session_count: number;
  sessions: {
    date: string;
    sets_count: number;
    max_weight_kg: number;
    reps_at_max_weight: number;
  }[];
  total_weight_change_kg: number | null;
  consecutive_weight_changes_kg: number[];
};

type FoodEntry = {
  id: string;
  consumed_on: string;
  quantity: number | string;
  unit: string;
  energy_kcal_100_snapshot: number | string | null;
  protein_g_100_snapshot: number | string | null;
  carbs_g_100_snapshot: number | string | null;
  fat_g_100_snapshot: number | string | null;
};

type WeightLog = {
  measured_on: string;
  weight_kg: number | string;
};

type GoalRecord = {
  goal_type: string;
  daily_calorie_target: number | null;
  daily_protein_target_g: number | string | null;
  daily_carbs_target_g: number | string | null;
  daily_fat_target_g: number | string | null;
  effective_at: string;
};

type NutritionMetric = "calories_kcal" | "protein_g" | "carbs_g" | "fat_g";

type NutritionDay = {
  date: string;
  entries: number;
  calories_kcal: number | null;
  protein_g: number | null;
  carbs_g: number | null;
  fat_g: number | null;
};

type NutritionSummary = {
  days_with_records: number;
  entries_count: number;
  days_with_complete_calorie_snapshots: number;
  average_per_day_with_records: {
    calories_kcal: number | null;
    protein_g: number | null;
    carbs_g: number | null;
    fat_g: number | null;
  };
  last_7_days_with_records: NutritionDay[];
  note: string;
};

type WeightTrend = {
  measurements: number;
  first_date: string | null;
  first_kg: number | null;
  last_date: string | null;
  last_kg: number | null;
  change_kg: number | null;
};

function json(body: unknown, status = 200): Response {
  return Response.json(body, { status });
}

function formatKg(value: number): string {
  return `${value.toLocaleString("es-CL", {
    maximumFractionDigits: 2,
  })} kg`;
}

function signedKg(value: number): string {
  const sign = value > 0 ? "+" : "";
  return `${sign}${formatKg(value)}`;
}

function getNextUtcMidnight(): string {
  const next = new Date();
  next.setUTCHours(24, 0, 0, 0);
  return next.toISOString();
}

function buildTrends(
  sessions: TrainingSession[],
): ExerciseTrend[] {
  const records: SessionRecord[] = [];

  for (const session of sessions) {
    for (const item of session.workout_exercises ?? []) {
      const validSets = (item.performed_sets ?? [])
        .map((set) => ({
          weight_kg: Number(set.weight_kg),
          reps: Number(set.reps),
        }))
        .filter(
          (set) =>
            Number.isFinite(set.weight_kg) &&
            set.weight_kg >= 0 &&
            Number.isInteger(set.reps) &&
            set.reps > 0,
        );

      if (validSets.length === 0) {
        continue;
      }

      // Seleccionar la serie con mayor carga.
      // En caso de empate, preferir más repeticiones.
      const heaviest = validSets.reduce((best, current) => {
        if (current.weight_kg > best.weight_kg) {
          return current;
        }

        if (
          current.weight_kg === best.weight_kg &&
          current.reps > best.reps
        ) {
          return current;
        }

        return best;
      });

      const exercise = Array.isArray(item.exercises)
        ? item.exercises[0]
        : item.exercises;

      records.push({
        exercise_id: item.exercise_id,
        exercise_name: exercise?.name ?? "Ejercicio",
        date: session.performed_on,
        sets_count: validSets.length,
        max_weight_kg: heaviest.weight_kg,
        reps_at_max_weight: heaviest.reps,
      });
    }
  }

  const grouped = new Map<string, SessionRecord[]>();

  for (const record of records) {
    const existing = grouped.get(record.exercise_id) ?? [];
    existing.push(record);
    grouped.set(record.exercise_id, existing);
  }

  return Array.from(grouped.values()).map((items) => {
    const ordered = [...items].sort(
      (a, b) => a.date.localeCompare(b.date),
    );

    const first = ordered[0];
    const last = ordered[ordered.length - 1];

    const changes = ordered.slice(1).map(
      (item, index) =>
        Number(
          (
            item.max_weight_kg -
            ordered[index].max_weight_kg
          ).toFixed(2),
        ),
    );

    return {
      exercise: first.exercise_name,
      session_count: ordered.length,
      sessions: ordered.map((item) => ({
        date: item.date,
        sets_count: item.sets_count,
        max_weight_kg: item.max_weight_kg,
        reps_at_max_weight: item.reps_at_max_weight,
      })),
      total_weight_change_kg:
        ordered.length >= 2
          ? Number(
              (
                last.max_weight_kg -
                first.max_weight_kg
              ).toFixed(2),
            )
          : null,
      consecutive_weight_changes_kg: changes,
    };
  });
}


function buildFactualSummary(
  trends: ExerciseTrend[],
): string {
  return trends.slice(0, 3).map((trend) => {
    const first = trend.sessions[0];
    const last = trend.sessions[trend.sessions.length - 1];

    if (trend.session_count === 1) {
      return (
        `${trend.exercise}: una sesión registrada con ` +
        `${formatKg(first.max_weight_kg)} de carga máxima. ` +
        `Aún no hay suficientes sesiones para establecer una tendencia.`
      );
    }

    return (
      `${trend.exercise}: ${trend.session_count} sesiones; ` +
      `carga máxima de ${formatKg(first.max_weight_kg)} a ` +
      `${formatKg(last.max_weight_kg)} ` +
      `(variación ${signedKg(trend.total_weight_change_kg ?? 0)}). ` +
      `Repeticiones en la serie de mayor carga: ` +
      `${first.reps_at_max_weight} al inicio y ` +
      `${last.reps_at_max_weight} al final.`
    );
  }).join(" ");
}


// El backend hace los cálculos; la IA solo interpreta evidencia.
function round2(n: number): number {
  return Math.round((n + Number.EPSILON) * 100) / 100;
}

function numericOrNull(value: number | string | null | undefined): number | null {
  if (value === null || value === undefined || value === "") return null;
  const parsed = Number(value);
  return Number.isFinite(parsed) && parsed >= 0 ? parsed : null;
}

const NUTRIENT_FIELDS: Record<NutritionMetric, keyof FoodEntry> = {
  calories_kcal: "energy_kcal_100_snapshot",
  protein_g: "protein_g_100_snapshot",
  carbs_g: "carbs_g_100_snapshot",
  fat_g: "fat_g_100_snapshot",
};

const NUTRIENT_KEYS = Object.keys(NUTRIENT_FIELDS) as NutritionMetric[];

function buildNutritionSummary(entries: FoodEntry[]): NutritionSummary {
  // 'Completo' aquí solo significa que todos los alimentos REGISTRADOS
  // tienen valores para ese nutriente, NO que el usuario registró todo lo comido.
  type DayAccumulator = {
    entries: number;
    totals: Record<NutritionMetric, number>;
    validSnapshots: Record<NutritionMetric, boolean>;
  };
  const byDate = new Map<string, DayAccumulator>();

  for (const entry of entries) {
    const day = byDate.get(entry.consumed_on) ?? {
      entries: 0,
      totals: { calories_kcal: 0, protein_g: 0, carbs_g: 0, fat_g: 0 },
      validSnapshots: {
        calories_kcal: true,
        protein_g: true,
        carbs_g: true,
        fat_g: true,
      },
    };
    day.entries++;
    const quantity = numericOrNull(entry.quantity);
    const validQuantity = quantity !== null && quantity > 0 &&
      (entry.unit === "g" || entry.unit === "ml");

    for (const metric of NUTRIENT_KEYS) {
      const per100 = numericOrNull(entry[NUTRIENT_FIELDS[metric]]);
      if (!validQuantity || per100 === null) {
        day.validSnapshots[metric] = false;
      } else {
        day.totals[metric] += (quantity * per100) / 100;
      }
    }
    byDate.set(entry.consumed_on, day);
  }

  const days: NutritionDay[] = Array.from(byDate.entries())
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([date, item]) => ({
      date,
      entries: item.entries,
      calories_kcal: item.validSnapshots.calories_kcal
        ? round2(item.totals.calories_kcal) : null,
      protein_g: item.validSnapshots.protein_g
        ? round2(item.totals.protein_g) : null,
      carbs_g: item.validSnapshots.carbs_g
        ? round2(item.totals.carbs_g) : null,
      fat_g: item.validSnapshots.fat_g
        ? round2(item.totals.fat_g) : null,
    }));

  const averages = {} as NutritionSummary["average_per_day_with_records"];
  for (const metric of NUTRIENT_KEYS) {
    const validDays = days.filter((day) => day[metric] !== null);
    // Promedio solo de los días con datos nutricionales utilizables.
    averages[metric] = validDays.length > 0
      ? round2(validDays.reduce((sum, day) => sum + (day[metric] ?? 0), 0) /
        validDays.length)
      : null;
  }

  return {
    days_with_records: days.length,
    entries_count: entries.length,
    days_with_complete_calorie_snapshots:
      days.filter((day) => day.calories_kcal !== null).length,
    average_per_day_with_records: averages,
    last_7_days_with_records: days.slice(-7),
    note: "Los totales representan solo alimentos registrados. " +
      "Un día con registros NO acredita una alimentación diaria completa. " +
      "Si falta un dato nutricional de un alimento, el total de ese " +
      "nutriente para ese día se representa como null.",
  };
}

function buildWeightTrend(logs: WeightLog[]): WeightTrend {
  const valid = logs.map((log) => ({
    date: log.measured_on,
    kg: numericOrNull(log.weight_kg),
  })).filter((log): log is { date: string; kg: number } =>
    log.kg !== null && log.kg > 0
  ).sort((a, b) => a.date.localeCompare(b.date));

  const first = valid[0];
  const last = valid[valid.length - 1];
  return {
    measurements: valid.length,
    first_date: first?.date ?? null,
    first_kg: first?.kg ?? null,
    last_date: last?.date ?? null,
    last_kg: last?.kg ?? null,
    change_kg: valid.length >= 2 ? round2(last.kg - first.kg) : null,
  };
}

function buildCombinedSummary(
  goal: GoalRecord | null,
  weight: WeightTrend,
  nutrition: NutritionSummary,
  trends: ExerciseTrend[],
): string {
  const goalNames: Record<string, string> = {
    gain_muscle: "aumentar masa muscular",
    lose_weight: "reducir peso",
    maintain_weight: "mantener peso",
  };
  const parts: string[] = [];
  if (goal) {
    parts.push(`Objetivo vigente: ${goalNames[goal.goal_type] ?? goal.goal_type}.`);
  }
  if (weight.measurements >= 2 && weight.change_kg !== null) {
    parts.push(
      `Peso: ${weight.measurements} mediciones; ` +
      `${formatKg(weight.first_kg!)} (${weight.first_date}) a ` +
      `${formatKg(weight.last_kg!)} (${weight.last_date}), ` +
      `variación ${signedKg(weight.change_kg)}. ` +
      `El peso por sí solo no permite inferir cambios de músculo o grasa.`,
    );
  } else if (weight.measurements === 1) {
    parts.push(`Peso: una medición de ${formatKg(weight.last_kg!)}; ` +
      "aún no es posible establecer una tendencia.");
  }
  if (nutrition.days_with_records > 0) {
    const average = nutrition.average_per_day_with_records.calories_kcal;
    parts.push(
      `Alimentación: ${nutrition.entries_count} registros de alimentación en ` +
      `${nutrition.days_with_records} días con registros. ` +
      (average === null ? "No hay datos calóricos completos de los productos registrados. " :
        `Promedio de calorías registradas en días con datos utilizables: ${average} kcal. `) +
      "Es un registro parcial posible, no una estimación de todo lo consumido.",
    );
  }
  if (trends.length > 0) parts.push(buildFactualSummary(trends));
  
  let result = "";

  for (const part of parts) {
    const next = result ? `${result} ${part}` : part;

    if (next.length > 1200) {
      break;
    }

    result = next;
  }

  return result;

}


const ANALYSIS_SYSTEM_PROMPT =
  "Eres un asistente de análisis de entrenamiento y nutrición " +
  "de una aplicación de seguimiento físico. " +
  "Responde en español de Chile con entre 1 y 3 sugerencias " +
  "concretas, prudentes y personalizadas. " +

  "Cada sugerencia debe estar fundamentada en al menos un dato " +
  "disponible del usuario. Evita consejos genéricos que podrían " +
  "aplicarse a cualquier persona. " +

  "Analiza conjuntamente el objetivo vigente, las tendencias " +
  "de entrenamiento, las mediciones de peso corporal y los " +
  "registros de alimentación, cuando existan. " +

  "Prioriza las observaciones según su importancia. " +
  "Si aparece una variación de peso llamativa respecto al " +
  "intervalo entre mediciones, recomienda verificar fechas, " +
  "mediciones y condiciones del registro antes de interpretarla " +
  "como progreso. Si el cambio es real o persiste, puede ser " +
  "apropiado consultar a un profesional de salud. " +
  "No diagnostiques ni atribuyas causas sin evidencia. " +

  "Al analizar entrenamiento, considera conjuntamente las " +
  "cargas, repeticiones y cantidad de sesiones. " +
  "No interpretes un aumento pequeño de carga como prueba " +
  "de ganancia muscular. No asegures que la progresión continuará. " +

  "No presupongas que el usuario no realiza cardio, no descansa " +
  "adecuadamente o no practica otras actividades simplemente " +
  "porque no aparecen registradas. " +
  "No recomiendes aumentar frecuencia, volumen o intensidad " +
  "si los datos no justifican esa modificación. " +

  "En nutrición, distingue siempre entre alimentos registrados " +
  "y alimentación total. Un día con registros no demuestra " +
  "que el usuario haya registrado todas sus comidas. " +
  "Una meta calórica no equivale a calorías consumidas. " +
  "No afirmes que existe déficit, superávit o cumplimiento " +
  "de objetivos nutricionales a partir de registros parciales. " +

  "Los promedios nutricionales corresponden solamente a los " +
  "días con registros utilizables. No los presentes como " +
  "promedios de todos los días del período. " +
  "No compares registros históricos con una meta actual " +
  "suponiendo que esa meta estuvo vigente anteriormente. " +

  "El backend ya realizó los cálculos numéricos. " +
  "No los recalcules ni inventes cifras o datos ausentes. " +
  "No infieras pérdida de grasa, aumento de músculo, lesiones, " +
  "fatiga, recuperación o calidad técnica sin evidencia. " +

  "Si los registros son insuficientes o contradictorios, " +
  "reconócelo y propone una forma concreta de mejorar " +
  "el seguimiento. " +

  "No generes rutinas completas, no hagas diagnósticos y " +
  "no propongas restricciones calóricas extremas. " +
  "Las sugerencias son opcionales y el usuario decide si " +
  "las aplica. Nunca afirmes que modificaste sus registros, " +
  "objetivos o rutinas. " +

  "Devuelve únicamente las sugerencias solicitadas por el " +
  "esquema JSON. Cada sugerencia debe tener como máximo " +
  "250 caracteres y expresar una idea principal.";


Deno.serve(async (req: Request) => {
  if (req.method !== "POST") {
    return json(
      { error: "Método no permitido", code: "METHOD_NOT_ALLOWED" },
      405,
    );
  }

  const authHeader = req.headers.get("Authorization");

  if (!authHeader?.startsWith("Bearer ")) {
    return json(
      { error: "Autenticación requerida", code: "AUTH_REQUIRED" },
      401,
    );
  }

  const supabaseUrl = Deno.env.get("SUPABASE_URL");
  const supabaseAnonKey = Deno.env.get("SUPABASE_ANON_KEY");
  const groqApiKey = Deno.env.get("GROQ_API_KEY");

  if (!supabaseUrl || !supabaseAnonKey || !groqApiKey) {
    console.error("Falta configuración de Supabase o Groq");

    return json(
      { error: "Servicio no configurado", code: "CONFIG_ERROR" },
      500,
    );
  }

  const supabase = createClient(
    supabaseUrl,
    supabaseAnonKey,
    {
      global: {
        headers: {
          Authorization: authHeader,
        },
      },
    },
  );

  const {
    data: { user },
    error: authError,
  } = await supabase.auth.getUser();

  if (authError || !user) {
    return json(
      {
        error: "Sesión inválida o expirada",
        code: "INVALID_SESSION",
      },
      401,
    );
  }


  // Fechas UTC: 30 fechas de calendario contando el día de hoy.
  const today = new Date();
  const since = new Date(today);
  since.setUTCDate(since.getUTCDate() - (HISTORY_DAYS - 1));
  const sinceDate = since.toISOString().slice(0, 10);
  const todayDate = today.toISOString().slice(0, 10);

  // Consultar el contexto personal del usuario autenticado.
  const [profileResult, goalResult, weightResult] =
    await Promise.all([
      supabase
        .from("profiles")
        .select("age_years, height_cm, sex_for_formula")
        .eq("user_id", user.id)
        .maybeSingle(),

      supabase
        .from("user_goals")
        .select(`
          goal_type,
          daily_calorie_target,
          daily_protein_target_g,
          daily_carbs_target_g,
          daily_fat_target_g,
          effective_at
        `)
        .eq("user_id", user.id)
        .lte("effective_at", new Date().toISOString())
        .order("effective_at", { ascending: false })
        .order("id", { ascending: false })
        .limit(1)
        .maybeSingle(),

      supabase
        .from("body_weight_logs")
        .select("measured_on, weight_kg")
        .eq("user_id", user.id)
        .gte("measured_on", sinceDate)
        .lte("measured_on", todayDate)
        .order("measured_on", { ascending: true })
        .limit(30),
    ]);

  if (
    profileResult.error ||
    goalResult.error ||
    weightResult.error
  ) {
    console.error("Error consultando contexto del usuario", {
      profile: profileResult.error?.code,
      goal: goalResult.error?.code,
      weight: weightResult.error?.code,
    });

    return json(
      {
        error: "No se pudo consultar el contexto del usuario",
        code: "USER_CONTEXT_ERROR",
      },
      500,
    );
  }

  const profile = profileResult.data;
  const currentGoal = goalResult.data as GoalRecord | null;
  const weightLogs = (weightResult.data ?? []) as WeightLog[];

  // Paginar para no perder alimentos después de las primeras 1000 filas.
  // Limitar a 5000 protege al servicio de historiales excesivos.
  const nutritionRecords: FoodEntry[] = [];
  const PAGE_SIZE = 500;
  const MAX_FOOD_ENTRIES = 5000;
  for (let offset = 0; offset < MAX_FOOD_ENTRIES; offset += PAGE_SIZE) {
    const { data: page, error: foodError } = await supabase
      .from("food_entries")
      .select(`
        id, consumed_on, quantity, unit,
        energy_kcal_100_snapshot, protein_g_100_snapshot,
        carbs_g_100_snapshot, fat_g_100_snapshot
      `)
      .eq("user_id", user.id)
      .gte("consumed_on", sinceDate)
      .lte("consumed_on", todayDate)
      .order("consumed_on", { ascending: true })
      .order("id", { ascending: true })
      .range(offset, offset + PAGE_SIZE - 1);

    if (foodError) {
      console.error("Error consultando alimentación:", foodError.code, foodError.message);
      return json({ error: "No se pudo consultar la alimentación", code: "FOOD_QUERY_ERROR" }, 500);
    }
    nutritionRecords.push(...(page ?? []) as FoodEntry[]);
    if ((page ?? []).length < PAGE_SIZE) break;
  }
  if (nutritionRecords.length >= MAX_FOOD_ENTRIES) {
    return json({
      error: "El historial de alimentación supera el máximo analizable de 5000 registros",
      code: "FOOD_HISTORY_TOO_LARGE",
    }, 422);
  }

  const { data, error: workoutsError } = await supabase
    .from("workout_sessions")
    .select(`
      id,
      performed_on,
      workout_exercises (
        exercise_id,
        exercises (name),
        performed_sets (
          set_number,
          weight_kg,
          reps
        )
      )
    `)
    .eq("user_id", user.id)
    .gte("performed_on", sinceDate)
    .lte("performed_on", todayDate)
    .order("performed_on", { ascending: true })
    .limit(30);

  if (workoutsError) {
    console.error(
      "Error consultando entrenamientos:",
      workoutsError.code,
      workoutsError.message,
    );

    return json(
      {
        error: "No se pudieron consultar los entrenamientos",
        code: "WORKOUT_QUERY_ERROR",
      },
      500,
    );
  }

  const sessions = (data ?? []) as TrainingSession[];
  const trends = buildTrends(sessions);

  const resetAt = getNextUtcMidnight();
  const nutritionSummary = buildNutritionSummary(nutritionRecords);
  const weightTrend = buildWeightTrend(weightLogs);

  // Sin observaciones suficientes no se gasta la cuota de Groq.
  // El perfil y la meta, por sí solos, no acreditan progreso.
  if (trends.length === 0 && nutritionRecords.length === 0 &&
      weightTrend.measurements < 2) {
    return json({
      ok: true,
      status: "no_data",
      source: "supabase",
      test_mode: false,
      summary: "Todavía no hay suficientes registros de entrenamiento, " +
        "alimentación o evolución del peso para analizar tu progreso.",
      suggestions: ["Registra entrenamientos, alimentos o al menos dos " +
        "mediciones de peso para obtener sugerencias personalizadas."],
      quota: {
        limit: DAILY_LIMIT,
        remaining: null,
        resets_at: resetAt,
      },
    });
  }

  // Ordenar para priorizar los ejercicios con más historial.
  trends.sort(
    (a, b) =>
      b.session_count - a.session_count ||
      a.exercise.localeCompare(b.exercise),
  );

  const selectedTrends = trends
    .slice(0, 5)
    .map((trend) => ({
      ...trend,
      sessions: trend.sessions.slice(-8),
      consecutive_weight_changes_kg:
        trend.consecutive_weight_changes_kg.slice(-7),
    }));

  const summary = buildCombinedSummary(
    currentGoal, weightTrend, nutritionSummary, trends,
  );

  // Consumir una solicitud ANTES de llamar a Groq.
  // Esta función SQL impide superar el límite diario.
  const { data: remaining, error: quotaError } =
    await supabase.rpc("claim_workout_analysis_slot");

  if (quotaError || typeof remaining !== "number") {
    console.error(
      "Error verificando el límite de IA:",
      quotaError?.code ?? "INVALID_QUOTA_RESPONSE",
    );

    return json(
      {
        error: "No se pudo verificar el límite diario",
        code: "QUOTA_ERROR",
      },
      500,
    );
  }

  if (remaining < 0) {
    return json(
      {
        error:
          "Alcanzaste el máximo de 10 análisis diarios",
        code: "DAILY_LIMIT_REACHED",
        quota: {
          limit: DAILY_LIMIT,
          remaining: 0,
          resets_at: resetAt,
        },
      },
      429,
    );
  }

  // Groq propone sugerencias, pero no calcula
  // el resumen numérico ni modifica entrenamientos.
  try {
    const groqResponse = await fetch(
      "https://api.groq.com/openai/v1/chat/completions",
      {
        method: "POST",
        headers: {
          Authorization: `Bearer ${groqApiKey}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          model: "openai/gpt-oss-20b",
          reasoning_effort: "low",
          temperature: 0.2,
          max_completion_tokens: 1000,
          messages: [
            {
              role: "system",
              content: ANALYSIS_SYSTEM_PROMPT,
            },
            {
              role: "user",
              content: JSON.stringify({
                period_days: HISTORY_DAYS,
                profile: profile ?? null,
                current_goal: currentGoal,
                weight_trend: weightTrend,
                nutrition_summary: nutritionSummary,
                exercise_trends: selectedTrends,
              }),
            },
          ],
          response_format: {
            type: "json_schema",
            json_schema: {
              name: "workout_suggestions",
              strict: true,
              schema: {
                type: "object",
                properties: {
                  suggestions: {
                    type: "array",
                    items: {
                      type: "string",
                    },
                  },
                },
                required: ["suggestions"],
                additionalProperties: false,
              },
            },
          },
        }),
      },
    );

    if (!groqResponse.ok) {
      console.error("Groq HTTP:", groqResponse.status);

      return json(
        {
          error:
            groqResponse.status === 429
              ? "Se alcanzó el límite del proveedor de IA"
              : "No se pudo generar el análisis",
          code:
            groqResponse.status === 429
              ? "GROQ_RATE_LIMIT"
              : "AI_PROVIDER_ERROR",
        },
        groqResponse.status === 429 ? 429 : 502,
      );
    }

    const groqData = await groqResponse.json();
    const content = groqData.choices?.[0]?.message?.content;

    if (typeof content !== "string") {
      return json(
        {
          error: "Respuesta de IA no disponible",
          code: "AI_EMPTY_RESPONSE",
        },
        502,
      );
    }

    const analysis = JSON.parse(content);

    if (!Array.isArray(analysis.suggestions)) {
      return json(
        {
          error: "Formato de sugerencias inválido",
          code: "AI_INVALID_RESPONSE",
        },
        502,
      );
    }

    const suggestions = analysis.suggestions
      .filter(
        (item: unknown): item is string =>
          typeof item === "string" &&
          item.trim().length > 0,
      )
      .slice(0, 3)
      .map((item: string) => item.trim().slice(0, 250));

    if (suggestions.length === 0) {
      return json(
        {
          error: "La IA no entregó sugerencias válidas",
          code: "AI_INVALID_RESPONSE",
        },
        502,
      );
    }

    return json({
      ok: true,
      status: "analyzed",
      source: "groq",
      test_mode: false,
      summary: summary,
      suggestions,
      quota: {
        limit: DAILY_LIMIT,
        remaining,
        resets_at: resetAt,
      },
    });
  } catch (error) {
    console.error(
      "Error durante el análisis:",
      error instanceof Error
        ? error.message
        : "Error desconocido",
    );

    return json(
      {
        error: "Error al comunicarse con la IA",
        code: "AI_COMMUNICATION_ERROR",
      },
      502,
    );
  }
});
