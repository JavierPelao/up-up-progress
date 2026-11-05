
-- Funciones internas no expuestas directamente por la API.
CREATE SCHEMA IF NOT EXISTS private;

-- La función pública necesitará llamar a la función privada.
GRANT USAGE ON SCHEMA private TO authenticated;

-- Escritura controlada de una meta manual.
CREATE FUNCTION private.insert_manual_nutrition_goal(
    p_goal_type TEXT,
    p_daily_calorie_target INTEGER,
    p_daily_protein_target_g NUMERIC,
    p_daily_carbs_target_g NUMERIC,
    p_daily_fat_target_g NUMERIC
)
RETURNS public.user_goals
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_user_id UUID;
    v_saved_goal public.user_goals;
BEGIN
    -- El usuario se obtiene del JWT, no de los parámetros.
    v_user_id := auth.uid();

    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'Usuario no autenticado';
    END IF;

    -- Validar objetivo físico.
    IF p_goal_type IS NULL OR p_goal_type NOT IN (
        'gain_muscle',
        'lose_weight',
        'maintain_weight',
        'body_recomposition'
    ) THEN
        RAISE EXCEPTION 'Objetivo físico no válido';
    END IF;

    -- Validar calorías.
    IF p_daily_calorie_target IS NULL
       OR p_daily_calorie_target <= 0 THEN
        RAISE EXCEPTION 'Meta calórica no válida';
    END IF;

    -- Validar macros y precisión NUMERIC(7,1).
    IF p_daily_protein_target_g IS NULL
       OR p_daily_carbs_target_g IS NULL
       OR p_daily_fat_target_g IS NULL
       OR p_daily_protein_target_g < 0
       OR p_daily_carbs_target_g < 0
       OR p_daily_fat_target_g < 0
       OR p_daily_protein_target_g > 999999.9
       OR p_daily_carbs_target_g > 999999.9
       OR p_daily_fat_target_g > 999999.9
       OR p_daily_protein_target_g <>
          round(p_daily_protein_target_g, 1)
       OR p_daily_carbs_target_g <>
          round(p_daily_carbs_target_g, 1)
       OR p_daily_fat_target_g <>
          round(p_daily_fat_target_g, 1) THEN
        RAISE EXCEPTION 'Macronutrientes no válidos';
    END IF;

    INSERT INTO public.user_goals (
        user_id,
        goal_type,
        nutrition_goal_mode,
        daily_calorie_target,
        daily_protein_target_g,
        daily_carbs_target_g,
        daily_fat_target_g
    )
    VALUES (
        v_user_id,
        p_goal_type,
        'manual',
        p_daily_calorie_target,
        p_daily_protein_target_g,
        p_daily_carbs_target_g,
        p_daily_fat_target_g
    )
    RETURNING * INTO v_saved_goal;

    RETURN v_saved_goal;
END;
$$;

-- Limitar quién puede ejecutar la función privada.
REVOKE ALL ON FUNCTION private.insert_manual_nutrition_goal(
    TEXT, INTEGER, NUMERIC, NUMERIC, NUMERIC
) FROM PUBLIC, anon, authenticated;

GRANT EXECUTE ON FUNCTION private.insert_manual_nutrition_goal(
    TEXT, INTEGER, NUMERIC, NUMERIC, NUMERIC
) TO authenticated;

-- RPC expuesta a usuarios autenticados.
-- Se ejecuta con los permisos del usuario que llama.
CREATE FUNCTION public.save_manual_nutrition_goal(
    p_goal_type TEXT,
    p_daily_calorie_target INTEGER,
    p_daily_protein_target_g NUMERIC,
    p_daily_carbs_target_g NUMERIC,
    p_daily_fat_target_g NUMERIC
)
RETURNS public.user_goals
LANGUAGE sql
SECURITY INVOKER
SET search_path = ''
AS $$
    SELECT private.insert_manual_nutrition_goal(
        p_goal_type,
        p_daily_calorie_target,
        p_daily_protein_target_g,
        p_daily_carbs_target_g,
        p_daily_fat_target_g
    );
$$;

REVOKE ALL ON FUNCTION public.save_manual_nutrition_goal(
    TEXT, INTEGER, NUMERIC, NUMERIC, NUMERIC
) FROM PUBLIC, anon, authenticated;

GRANT EXECUTE ON FUNCTION public.save_manual_nutrition_goal(
    TEXT, INTEGER, NUMERIC, NUMERIC, NUMERIC
) TO authenticated;
