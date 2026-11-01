
import { createClient } from "npm:@supabase/supabase-js@2";

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

const json = (body: unknown, status = 200) =>
  Response.json(body, { status });

Deno.serve(async (req: Request) => {
  if (req.method !== "POST") {
    return json({ error: "Método no permitido" }, 405);
  }

  const authHeader = req.headers.get("Authorization");

  if (!authHeader?.startsWith("Bearer ")) {
    return json({ error: "Autenticación requerida" }, 401);
  }

  const supabaseUrl = Deno.env.get("SUPABASE_URL");
  const supabaseAnonKey = Deno.env.get("SUPABASE_ANON_KEY");
  const groqApiKey = Deno.env.get("GROQ_API_KEY");

  if (!supabaseUrl || !supabaseAnonKey || !groqApiKey) {
    console.error("Falta configuración de Supabase o Groq");
    return json({ error: "Servicio no configurado" }, 500);
  }

  const supabase = createClient(supabaseUrl, supabaseAnonKey, {
    global: {
      headers: {
        Authorization: authHeader,
      },
    },
  });

  const {
    data: { user },
    error: authError,
  } = await supabase.auth.getUser();

  if (authError || !user) {
    return json({ error: "Sesión inválida o expirada" }, 401);
  }

  // Obtener los entrenamientos de los últimos 30 días.
  const since = new Date();
  since.setUTCDate(since.getUTCDate() - 30);

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
      { error: "No se pudieron consultar los entrenamientos" },
      500,
    );
  }

  const sessions = (data ?? []) as TrainingSession[];
  const records: SessionRecord[] = [];

  // Preparar los registros para el análisis.
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

      if (validSets.length === 0) continue;

      const heaviest = validSets.reduce((best, current) =>
        current.weight_kg > best.weight_kg ? current : best
      );

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

  // Cuando el usuario todavía no registra entrenamientos.
  if (records.length === 0) {
    return json({
      ok: true,
      source: "supabase",
      test_mode: false,
      summary: "Todavía no hay entrenamientos registrados en los últimos 30 días.",
      suggestions: [
        "Registra tus ejercicios, cargas y repeticiones para comenzar a analizar tu progreso.",
      ],
    });
  }

  // Agrupar las sesiones por ejercicio.
  const grouped = new Map<string, SessionRecord[]>();

  for (const record of records) {
    const existing = grouped.get(record.exercise_id) ?? [];
    existing.push(record);
    grouped.set(record.exercise_id, existing);
  }

  // Calcular los cambios de carga en nuestro backend.
  const trends = Array.from(grouped.values()).map((items) => {
    const ordered = [...items].sort(
      (a, b) => a.date.localeCompare(b.date),
    );

    const first = ordered[0];
    const last = ordered[ordered.length - 1];

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
              (last.max_weight_kg - first.max_weight_kg).toFixed(2),
            )
          : null,
      consecutive_weight_changes_kg: ordered
        .slice(1)
        .map((item, index) =>
          Number(
            (
              item.max_weight_kg -
              ordered[index].max_weight_kg
            ).toFixed(2),
          )
        ),
    };
  });

  const workoutSummary = {
    period_days: 30,
    trends,
  };

  // Enviar el resumen calculado a Groq.
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
                "Responde siempre en español. " +
                "Utiliza únicamente los registros proporcionados. " +
                "Las diferencias de carga ya están calculadas por el backend. " +
                "No vuelvas a calcularlas ni confundas el cambio total " +
                "con el cambio entre sesiones. " +
                "No afirmes mejoras de fuerza máxima ni causas fisiológicas " +
                "que los registros no permitan comprobar. " +
                "Si hay una sola sesión, indica que no existe " +
                "tendencia suficiente. " +
                "Entrega entre una y tres sugerencias breves y prudentes. " +
                "No inventes datos, no diagnostiques lesiones, " +
                "no generes rutinas completas y no afirmes " +
                "que modificaste entrenamientos u objetivos.",
            },
            {
              role: "user",
              content: JSON.stringify(workoutSummary),
            },
          ],
          response_format: {
            type: "json_schema",
            json_schema: {
              name: "workout_analysis",
              strict: true,
              schema: {
                type: "object",
                properties: {
                  summary: { type: "string" },
                  suggestions: {
                    type: "array",
                    items: { type: "string" },
                  },
                },
                required: ["summary", "suggestions"],
                additionalProperties: false,
              },
            },
          },
        }),
      },
    );

    if (!groqResponse.ok) {
      console.error("Groq HTTP:", groqResponse.status);

      if (groqResponse.status === 429) {
        return json(
          { error: "Se alcanzó el límite gratuito de IA" },
          429,
        );
      }

      return json(
        { error: "No se pudo generar el análisis" },
        502,
      );
    }

    const groqData = await groqResponse.json();
    const content = groqData.choices?.[0]?.message?.content;

    if (typeof content !== "string") {
      return json(
        { error: "Respuesta de IA no disponible" },
        502,
      );
    }

    const analysis = JSON.parse(content);

    if (
      typeof analysis.summary !== "string" ||
      !Array.isArray(analysis.suggestions) ||
      !analysis.suggestions.every(
        (item: unknown) => typeof item === "string",
      )
    ) {
      return json(
        { error: "Formato de análisis inválido" },
        502,
      );
    }

    return json({
      ok: true,
      source: "groq",
      test_mode: false,
      summary: analysis.summary.slice(0, 500),
      suggestions: analysis.suggestions
        .slice(0, 3)
        .map((item: string) => item.slice(0, 250)),
    });
  } catch (error) {
    console.error(
      "Error durante el análisis:",
      error instanceof Error ? error.message : "Error desconocido",
    );

    return json(
      { error: "Error al comunicarse con la IA" },
      502,
    );
  }
});
