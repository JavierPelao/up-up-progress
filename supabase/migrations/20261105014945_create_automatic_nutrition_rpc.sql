
-- Permitir que el rol de servicio utilice el esquema privado.
GRANT USAGE ON SCHEMA private TO service_role;

CREATE FUNCTION private.insert_automatic_nutrition_goal(
    p_user_id UUID,
    p_goal_type TEXT,
    p_activity_level TEXT,
    p_daily_calorie_target INTEGER,
    p_daily_protein_target_g NUMERIC,
    p_daily_carbs_target_g NUMERIC,
    p_daily_fat_target_g NUMERIC,
    p_calorie_adjustment_pct NUMERIC,
    p_calculation_weight_kg NUMERIC,
    p_estimated_tdee_kcal NUMERIC
)
RETURNS public.user_goals
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_current public.user_goals;
BEGIN
    IF p_user_id IS NULL THEN
        RAISE EXCEPTION 'Usuario requerido';
    END IF;

    -- Serializar los guardados del mismo usuario.
    PERFORM pg_catalog.pg_advisory_xact_lock(
        pg_catalog.hashtextextended(
            p_user_id::TEXT, 0
        )
    );

    -- Consultar la meta vigente.
    SELECT *
    INTO v_current
    FROM public.user_goals
    WHERE user_id = p_user_id
    ORDER BY effective_at DESC, id DESC
    LIMIT 1;

    -- Si todos los valores coinciden, no insertar.
    IF v_current.id IS NOT NULL
       AND v_current.nutrition_goal_mode = 'automatic'
       AND ROW(
           v_current.goal_type,
           v_current.activity_level,
           v_current.daily_calorie_target,
           v_current.daily_protein_target_g,
           v_current.daily_carbs_target_g,
           v_current.daily_fat_target_g,
           v_current.calorie_adjustment_pct,
           v_current.calculation_weight_kg,
           v_current.estimated_tdee_kcal
       ) IS NOT DISTINCT FROM ROW(
           p_goal_type,
           p_activity_level,
           p_daily_calorie_target,
           p_daily_protein_target_g,
           p_daily_carbs_target_g,
           p_daily_fat_target_g,
           p_calorie_adjustment_pct,
           p_calculation_weight_kg,
           p_estimated_tdee_kcal
       )
    THEN
        RETURN v_current;
    END IF;

    INSERT INTO public.user_goals (
        user_id,
        goal_type,
        activity_level,
        nutrition_goal_mode,
        daily_calorie_target,
        daily_protein_target_g,
        daily_carbs_target_g,
        daily_fat_target_g,
        calorie_adjustment_pct,
        calculation_weight_kg,
        estimated_tdee_kcal
    )
    VALUES (
        p_user_id,
        p_goal_type,
        p_activity_level,
        'automatic',
        p_daily_calorie_target,
        p_daily_protein_target_g,
        p_daily_carbs_target_g,
        p_daily_fat_target_g,
        p_calorie_adjustment_pct,
        p_calculation_weight_kg,
        p_estimated_tdee_kcal
    )
    RETURNING * INTO v_current;

    RETURN v_current;
END;
$$;

-- Solo service_role puede ejecutar la función privada.
REVOKE ALL ON FUNCTION private.insert_automatic_nutrition_goal(
    UUID, TEXT, TEXT, INTEGER, NUMERIC,
    NUMERIC, NUMERIC, NUMERIC, NUMERIC, NUMERIC
) FROM PUBLIC, anon, authenticated;

GRANT EXECUTE ON FUNCTION private.insert_automatic_nutrition_goal(
    UUID, TEXT, TEXT, INTEGER, NUMERIC,
    NUMERIC, NUMERIC, NUMERIC, NUMERIC, NUMERIC
) TO service_role;

-- Punto de entrada para la API.
-- No tiene privilegios elevados por sí mismo.
CREATE FUNCTION public.save_automatic_nutrition_goal(
    p_user_id UUID,
    p_goal_type TEXT,
    p_activity_level TEXT,
    p_daily_calorie_target INTEGER,
    p_daily_protein_target_g NUMERIC,
    p_daily_carbs_target_g NUMERIC,
    p_daily_fat_target_g NUMERIC,
    p_calorie_adjustment_pct NUMERIC,
    p_calculation_weight_kg NUMERIC,
    p_estimated_tdee_kcal NUMERIC
)
RETURNS public.user_goals
LANGUAGE sql
SECURITY INVOKER
SET search_path = ''
AS $$
    SELECT private.insert_automatic_nutrition_goal(
        p_user_id,
        p_goal_type,
        p_activity_level,
        p_daily_calorie_target,
        p_daily_protein_target_g,
        p_daily_carbs_target_g,
        p_daily_fat_target_g,
        p_calorie_adjustment_pct,
        p_calculation_weight_kg,
        p_estimated_tdee_kcal
    );
$$;

REVOKE ALL ON FUNCTION public.save_automatic_nutrition_goal(
    UUID, TEXT, TEXT, INTEGER, NUMERIC,
    NUMERIC, NUMERIC, NUMERIC, NUMERIC, NUMERIC
) FROM PUBLIC, anon, authenticated;

GRANT EXECUTE ON FUNCTION public.save_automatic_nutrition_goal(
    UUID, TEXT, TEXT, INTEGER, NUMERIC,
    NUMERIC, NUMERIC, NUMERIC, NUMERIC, NUMERIC
) TO service_role;
