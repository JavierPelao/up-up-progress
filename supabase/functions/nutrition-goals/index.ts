
import { createClient } from "npm:@supabase/supabase-js@2.119.0";

import {
  calculateAutomaticNutritionGoals,
  isActivityLevel,
  isPhysicalGoal,
  NutritionCalculationError,
} from "../_shared/nutrition-calculator.ts";

import { validateManualGoals } from "../_shared/manual-goals.ts";
import { validateWeightLog } from "../_shared/weight-log.ts";
import { validateOnboarding } from "../_shared/onboarding.ts";

const goalColumns = `
  id,
  goal_type,
  activity_level,
  nutrition_goal_mode,
  daily_calorie_target,
  daily_protein_target_g,
  daily_carbs_target_g,
  daily_fat_target_g,
  calorie_adjustment_pct,
  calculation_weight_kg,
  estimated_tdee_kcal,
  effective_at
`;

Deno.serve(async (req: Request) => {
  if (req.method !== "POST") {
    return Response.json(
      { error: "Método no permitido." },
      { status: 405 },
    );
  }

  const authorization = req.headers.get("Authorization");

  if (!authorization?.startsWith("Bearer ")) {
    return Response.json(
      { error: "Se requiere autenticación." },
      { status: 401 },
    );
  }

  const supabaseUrl = Deno.env.get("SUPABASE_URL");
  const anonKey = Deno.env.get("SUPABASE_ANON_KEY");

  if (!supabaseUrl || !anonKey) {
    return Response.json(
      { error: "Configuración incompleta." },
      { status: 500 },
    );
  }

  // Cliente con permisos del usuario: aplica RLS.
  const supabase = createClient(supabaseUrl, anonKey, {
    global: {
      headers: { Authorization: authorization },
    },
    auth: {
      autoRefreshToken: false,
      persistSession: false,
    },
  });

  const token = authorization.slice("Bearer ".length).trim();

  const {
    data: { user },
    error: authError,
  } = await supabase.auth.getUser(token);

  if (authError || !user) {
    return Response.json(
      { error: "Sesión no válida." },
      { status: 401 },
    );
  }

  const rawBody = await req.text();
  let payload: Record<string, unknown> | null = null;

  if (rawBody.trim()) {
    let parsed: unknown;

    try {
      parsed = JSON.parse(rawBody);
    } catch {
      return Response.json(
        { error: "JSON no válido." },
        { status: 400 },
      );
    }

    if (
      typeof parsed !== "object" ||
      parsed === null ||
      Array.isArray(parsed)
    ) {
      return Response.json(
        { error: "Se requiere un objeto JSON." },
        { status: 400 },
      );
    }

    payload = parsed as Record<string, unknown>;
  }

  // El alta se confirma en una sola transacción; el UUID procede del token verificado.
  if (payload?.action === "onboarding_status" || payload?.action === "complete_onboarding") {
    const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
    if (!serviceKey) {
      return Response.json({ error: "No se pudo preparar el guardado." }, { status: 500 });
    }
    const admin = createClient(supabaseUrl, serviceKey, {
      auth: { autoRefreshToken: false, persistSession: false },
    });
    if (payload.action === "onboarding_status") {
      const { data, error } = await admin.rpc("get_onboarding_state", { p_user_id: user.id });
      if (error) return Response.json({ error: "No se pudo consultar tu configuración." }, { status: 500 });
      return Response.json({ onboarding: data });
    }
    let input;
    try {
      input = validateOnboarding(payload);
    } catch (error) {
      return Response.json({
        error: error instanceof Error ? error.message : "Revisa los datos del perfil.",
        ...(error instanceof NutritionCalculationError ? { code: error.code } : {}),
      }, { status: error instanceof NutritionCalculationError ? 422 : 400 });
    }
    const { data, error } = await admin.rpc("complete_onboarding", { ...input, p_user_id: user.id });
    if (error) {
      return Response.json({ error: "No se pudieron guardar tus datos. Puedes volver a intentarlo." }, { status: 500 });
    }
    return Response.json({ onboarding: data });
  }

  // Las metas manuales se validan con los mismos límites y precisión de Kotlin.
  if (payload?.action === "save_manual") {
    let manualGoals;

    try {
      manualGoals = validateManualGoals(payload);
    } catch (error) {
      return Response.json(
        {
          error: error instanceof Error
            ? error.message
            : "Metas manuales inválidas.",
        },
        { status: 400 },
      );
    }

    const { data: savedGoal, error: saveError } = await supabase
      .rpc("save_manual_nutrition_goal", {
        p_goal_type: manualGoals.goalType,
        p_activity_level: manualGoals.activityLevel,
        p_daily_calorie_target:
          manualGoals.dailyCalorieTarget,
        p_daily_protein_target_g:
          manualGoals.dailyProteinTargetG,
        p_daily_carbs_target_g:
          manualGoals.dailyCarbsTargetG,
        p_daily_fat_target_g:
          manualGoals.dailyFatTargetG,
      })
      .select(goalColumns)
      .single();

    if (saveError) {
      console.error("Error RPC manual:", saveError.message);

      return Response.json(
        { error: "No se pudo guardar la meta manual." },
        { status: 500 },
      );
    }

    return Response.json(
      {
        message: "Meta manual guardada correctamente.",
        savedGoal,
      },
      { status: 201 },
    );
  }

  // 2. NUEVO: GUARDAR O ACTUALIZAR UN PESO.
  const isSavingWeight = payload?.action === "save_weight";
  let savedWeight = null;

  if (isSavingWeight) {
    let weightInput;

    try {
      weightInput = validateWeightLog(payload!);
    } catch (error) {
      return Response.json(
        {
          error: error instanceof Error
            ? error.message
            : "Registro de peso inválido.",
        },
        { status: 400 },
      );
    }

    const { data, error } = await supabase
      .from("body_weight_logs")
      .upsert(
        {
          user_id: user.id,
          measured_on: weightInput.measuredOn,
          weight_kg: weightInput.weightKg,
        },
        {
          onConflict: "user_id,measured_on",
        },
      )
      .select("id, measured_on, weight_kg")
      .single();

    if (error) {
      console.error("Error guardando peso:", error.message);

      return Response.json(
        { error: "No se pudo guardar el peso." },
        { status: 500 },
      );
    }

    savedWeight = data;
  }

  // 3. OBTENER PERFIL.
  const { data: profile, error: profileError } = await supabase
    .from("profiles")
    .select("age_years, height_cm, sex_for_formula")
    .eq("user_id", user.id)
    .maybeSingle();

  if (profileError) {
    return Response.json(
      {
        error: "No se pudo consultar el perfil.",
        ...(isSavingWeight ? { weightSaved: true, savedWeight } : {}),
      },
      { status: 500 },
    );
  }

  // Siempre utilizar el último peso por fecha.
  const { data: weight, error: weightError } = await supabase
    .from("body_weight_logs")
    .select("weight_kg, measured_on")
    .eq("user_id", user.id)
    .order("measured_on", { ascending: false })
    .limit(1)
    .maybeSingle();

  if (weightError) {
    return Response.json(
      {
        error: "No se pudo consultar el último peso.",
        ...(isSavingWeight ? { weightSaved: true, savedWeight } : {}),
      },
      { status: 500 },
    );
  }

  const ageYears = profile?.age_years ?? null;
  const heightCm = profile?.height_cm == null
    ? null
    : Number(profile.height_cm);
  const sexForFormula = profile?.sex_for_formula ?? null;
  const weightKg = weight?.weight_kg == null
    ? null
    : Number(weight.weight_kg);

  const missingFields: string[] = [];

  if (ageYears === null) missingFields.push("ageYears");
  if (heightCm === null) missingFields.push("heightCm");
  if (sexForFormula === null) {
    missingFields.push("sexForFormula");
  }
  if (weightKg === null) missingFields.push("weightKg");

  const baseResponse = {
    profile: profile
      ? { ageYears, heightCm, sexForFormula }
      : null,
    currentWeight: weight
      ? { weightKg, measuredOn: weight.measured_on }
      : null,
    calculationReadiness: {
      canCalculate: missingFields.length === 0,
      missingFields,
    },
  };

  // Consultar perfil y último peso sin guardar.
  if (payload === null) {
    return Response.json(baseResponse);
  }

  const action = payload.action;

  if (
    action !== "preview" &&
    action !== "save_automatic" &&
    action !== "save_weight"
  ) {
    return Response.json(
      { error: "Acción no válida." },
      { status: 400 },
    );
  }

  // 4. LEER META VIGENTE SI NECESITAMOS GUARDAR.
  let previousGoal = null;

  if (action !== "preview") {
    const { data, error } = await supabase
      .from("user_goals")
      .select(goalColumns)
      .eq("user_id", user.id)
      .order("effective_at", { ascending: false })
      .order("id", { ascending: false })
      .limit(1)
      .maybeSingle();

    if (error) {
      return Response.json(
        {
          error: "No se pudo consultar la meta vigente.",
          ...(isSavingWeight
            ? { weightSaved: true, savedWeight }
            : {}),
        },
        { status: 500 },
      );
    }

    previousGoal = data;
  }

  // Si se guardó un peso y NO existe meta automática,
  // no debemos crear ni modificar metas nutricionales.
  if (
    isSavingWeight &&
    previousGoal?.nutrition_goal_mode !== "automatic"
  ) {
    return Response.json({
      message: previousGoal?.nutrition_goal_mode === "manual"
        ? "Peso guardado. La meta manual se mantiene."
        : "Peso guardado. Todavía no hay una meta automática.",
      weightSaved: true,
      savedWeight,
      goalUpdated: false,
      currentGoal: previousGoal,
    });
  }

  // Al registrar peso se reutilizan los parámetros
  // de la meta automática que ya estaba vigente.
  const activityLevel = isSavingWeight
    ? previousGoal?.activity_level
    : payload.activityLevel;

  const goalType = isSavingWeight
    ? previousGoal?.goal_type
    : payload.goalType;

  if (
    !isActivityLevel(activityLevel)
  ) {
    return Response.json(
      {
        error: "Nivel de actividad no válido.",
        ...(isSavingWeight
          ? { weightSaved: true, savedWeight }
          : {}),
      },
      { status: 400 },
    );
  }

  if (
    !isPhysicalGoal(goalType)
  ) {
    return Response.json(
      {
        error: "Objetivo físico no válido.",
        ...(isSavingWeight
          ? { weightSaved: true, savedWeight }
          : {}),
      },
      { status: 400 },
    );
  }

  if (missingFields.length > 0) {
    return Response.json(
      {
        ...baseResponse,
        error: "Faltan datos para calcular las metas.",
        ...(isSavingWeight
          ? { weightSaved: true, savedWeight }
          : {}),
      },
      { status: 422 },
    );
  }

  // Cálculo definitivo con las mismas reglas y redondeos de la vista previa Kotlin.
  let nutritionResult;

  try {
    nutritionResult = calculateAutomaticNutritionGoals({
      ageYears: ageYears!,
      heightCm: heightCm!,
      sexForFormula: sexForFormula as "male" | "female",
      weightKg: weightKg!,
      activityLevel,
      goalType,
    });
  } catch (error) {
    console.error("Error calculando metas:", error);

    return Response.json(
      {
        error: error instanceof NutritionCalculationError ? error.message : "No se pudieron calcular las metas.",
        ...(error instanceof NutritionCalculationError ? { code: error.code } : {}),
        ...(isSavingWeight
          ? { weightSaved: true, savedWeight }
          : {}),
      },
      { status: 422 },
    );
  }

  if (action === "preview") {
    return Response.json({
      ...baseResponse,
      nutritionPreview: {
        nutritionGoalMode: "automatic",
        activityLevel,
        goalType,
        ...nutritionResult,
      },
    });
  }

  // 6. GUARDAR AUTOMÁTICAMENTE MEDIANTE RPC.
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");

  if (!serviceKey) {
    console.error("Falta SUPABASE_SERVICE_ROLE_KEY.");

    return Response.json(
      {
        error: "Configuración del servidor incompleta.",
        ...(isSavingWeight
          ? { weightSaved: true, savedWeight }
          : {}),
      },
      { status: 500 },
    );
  }

  // No reenviar el JWT del usuario a este cliente.
  const serviceClient = createClient(supabaseUrl, serviceKey, {
    auth: {
      autoRefreshToken: false,
      persistSession: false,
    },
  });

  const { data: savedGoal, error: saveError } =
    await serviceClient
      .rpc("save_automatic_nutrition_goal", {
        p_user_id: user.id,
        p_goal_type: goalType,
        p_activity_level: activityLevel,
        p_daily_calorie_target:
          nutritionResult.dailyCalorieTarget,
        p_daily_protein_target_g:
          nutritionResult.dailyProteinTargetG,
        p_daily_carbs_target_g:
          nutritionResult.dailyCarbsTargetG,
        p_daily_fat_target_g:
          nutritionResult.dailyFatTargetG,
        p_calorie_adjustment_pct:
          nutritionResult.calorieAdjustmentPct,
        p_calculation_weight_kg:
          nutritionResult.calculationWeightKg,
        p_estimated_tdee_kcal:
          nutritionResult.estimatedTdeeKcal,
      })
      .select(goalColumns)
      .single();

  if (saveError) {
    console.error("Error RPC automática:", saveError.message);

    return Response.json(
      {
        error: "No se pudo guardar la meta automática.",
        ...(isSavingWeight
          ? { weightSaved: true, savedWeight }
          : {}),
      },
      { status: 500 },
    );
  }

  const alreadyUpToDate = previousGoal?.id === savedGoal.id;

  if (isSavingWeight) {
    return Response.json({
      message: alreadyUpToDate
        ? "Peso guardado. La meta automática no necesitó cambios."
        : "Peso guardado y meta automática recalculada.",
      weightSaved: true,
      savedWeight,
      goalUpdated: !alreadyUpToDate,
      savedGoal,
    });
  }

  // Conservar la respuesta de save_automatic.
  return Response.json(
    {
      message: alreadyUpToDate
        ? "La meta automática ya está actualizada."
        : "Meta nutricional guardada correctamente.",
      alreadyUpToDate,
      savedGoal,
    },
    { status: alreadyUpToDate ? 200 : 201 },
  );
});
