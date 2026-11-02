
import { createClient } from "npm:@supabase/supabase-js@2";

const DAILY_LIMIT = 3;
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

// Construimos el resumen numérico sin depender de la IA.
function buildFactualSummary(
  trends: ExerciseTrend[],
): string {
  const descriptions = trends.slice(0, 3).map((trend) => {
    const first = trend.sessions[0];
    const last = trend.sessions[trend.sessions.length - 1];

    if (trend.session_count === 1) {
      return (
        `${trend.exercise}: una sesión registrada, ` +
        `con una carga máxima de ${formatKg(first.max_weight_kg)} ` +
        `y ${first.reps_at_max_weight} repeticiones ` +
        `en esa serie. Aún no hay suficientes sesiones ` +
        `para establecer una tendencia.`
      );
    }

    const totalChange = trend.total_weight_change_kg ?? 0;

    const intermediateChanges =
      trend.consecutive_weight_changes_kg
        .map(signedKg)
        .join(", ");

    return (
      `${trend.exercise}: ${trend.session_count} registros. ` +
      `La carga máxima registrada pasó de ` +
      `${formatKg(first.max_weight_kg)} a ` +
      `${formatKg(last.max_weight_kg)} ` +
      `(cambio total: ${signedKg(totalChange)}). ` +
      `Cambios entre registros consecutivos: ` +
      `${intermediateChanges}. ` +
      `Repeticiones en la serie de mayor carga: ` +
      `${first.reps_at_max_weight} al inicio y ` +
      `${last.reps_at_max_weight} al final.`
    );
  });

  return descriptions.join(" ");
}

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

  // Buscar entrenamientos de los últimos 30 días.
  const since = new Date();
  since.setUTCDate(since.getUTCDate() - HISTORY_DAYS);

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
    .gte("performed_on", since.toISOString().slice(0, 10))
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

  // Sin historial no gastamos solicitudes de Groq.
  if (trends.length === 0) {
    return json({
      ok: true,
      status: "no_data",
      source: "supabase",
      test_mode: false,
      summary:
        "Todavía no hay entrenamientos registrados " +
        "en los últimos 30 días.",
      suggestions: [
        "Registra tus ejercicios, cargas y repeticiones " +
        "para comenzar a analizar tu progreso.",
      ],
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

  const summary = buildFactualSummary(trends);

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

  if (remaining === 0) {
    // El valor 0 también se devuelve cuando se consume
    // exitosamente la tercera solicitud.
    // Para distinguir ambos casos, la función SQL
    // se ajustará a devolver -1 al superar el límite.
  }

  if (remaining < 0) {
    return json(
      {
        error:
          "Alcanzaste el máximo de 3 análisis diarios",
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
              content:
                "Eres un asistente de análisis de entrenamientos. " +
                "Responde siempre en español de Chile, " +
                "con sugerencias breves, prudentes y concretas. " +
                "Utiliza únicamente los registros proporcionados. " +
                "Las diferencias de cargas fueron calculadas " +
                "por el backend: no las recalcules. " +
                "Nunca confundas el cambio total " +
                "con cambios entre sesiones. " +
                "No prescribas aumentos fijos de peso por semana. " +
                "No asegures que una progresión continuará. " +
                "No infieras fatiga, descanso, recuperación, " +
                "lesiones ni calidad de la técnica si esos " +
                "datos no fueron registrados. " +
                "No afirmes mejoras de fuerza máxima " +
                "a partir de estas cargas solamente. " +
                "Cuando exista una sola sesión, " +
                "evita afirmar tendencias. " +
                "Sugiere opciones que el usuario puede evaluar, " +
                "sin imponer cambios. " +
                "No generes rutinas completas ni diagnósticos. " +
                "Nunca digas que modificaste sus registros. " +
                "Devuelve entre una y tres sugerencias.",
            },
            {
              role: "user",
              content: JSON.stringify({
                period_days: HISTORY_DAYS,
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
      summary: summary.slice(0, 1200),
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
