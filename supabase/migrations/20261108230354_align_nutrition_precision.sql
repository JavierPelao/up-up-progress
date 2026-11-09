-- Alineación del contrato nutricional entre Android y Supabase.
-- RF-04, RNF-SEG-06 y RNF-FIA-05. No borra ni recalcula registros históricos.
-- Contrato acordado: light, high y very_high; fórmula Kotlin, macros con dos decimales.
BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '60s';

-- Completar las columnas del perfil que ya existen en el servidor inspeccionado.
ALTER TABLE public.profiles
    ADD COLUMN IF NOT EXISTS age_years smallint,
    ADD COLUMN IF NOT EXISTS height_cm numeric(5,1),
    ADD COLUMN IF NOT EXISTS sex_for_formula text;
ALTER TABLE public.profiles DROP CONSTRAINT IF EXISTS profiles_age_years_check;
ALTER TABLE public.profiles ADD CONSTRAINT profiles_age_years_check CHECK (age_years BETWEEN 19 AND 120) NOT VALID;
ALTER TABLE public.profiles DROP CONSTRAINT IF EXISTS profiles_height_cm_check;
ALTER TABLE public.profiles ADD CONSTRAINT profiles_height_cm_check CHECK (height_cm BETWEEN 100 AND 250) NOT VALID;
ALTER TABLE public.profiles DROP CONSTRAINT IF EXISTS profiles_sex_for_formula_check;
ALTER TABLE public.profiles ADD CONSTRAINT profiles_sex_for_formula_check CHECK (sex_for_formula IN ('male','female')) NOT VALID;

-- Incorporar las columnas del contrato remoto que faltaban en las migraciones locales.
ALTER TABLE public.user_goals
    ADD COLUMN IF NOT EXISTS activity_level text,
    ADD COLUMN IF NOT EXISTS nutrition_goal_mode text,
    ADD COLUMN IF NOT EXISTS daily_protein_target_g numeric(8,2),
    ADD COLUMN IF NOT EXISTS daily_carbs_target_g numeric(8,2),
    ADD COLUMN IF NOT EXISTS daily_fat_target_g numeric(8,2),
    ADD COLUMN IF NOT EXISTS calorie_adjustment_pct numeric(5,2),
    ADD COLUMN IF NOT EXISTS calculation_weight_kg numeric(6,2),
    ADD COLUMN IF NOT EXISTS estimated_tdee_kcal numeric(13,6);

-- Se mantienen seis dígitos enteros en macros y siete en TDEE.
ALTER TABLE public.user_goals
    ALTER COLUMN daily_protein_target_g TYPE numeric(8,2),
    ALTER COLUMN daily_carbs_target_g TYPE numeric(8,2),
    ALTER COLUMN daily_fat_target_g TYPE numeric(8,2),
    ALTER COLUMN estimated_tdee_kcal TYPE numeric(13,6);

-- No validar filas históricas ni convertir sus objetivos automáticamente.
-- NOT VALID impone la regla en nuevas escrituras y conserva filas anteriores.
ALTER TABLE public.user_goals DROP CONSTRAINT IF EXISTS user_goals_goal_type_check;
ALTER TABLE public.user_goals ADD CONSTRAINT user_goals_goal_type_check
    CHECK (goal_type IN ('gain_muscle', 'lose_weight', 'maintain_weight')) NOT VALID;

ALTER TABLE public.user_goals DROP CONSTRAINT IF EXISTS user_goals_activity_level_check;
ALTER TABLE public.user_goals ADD CONSTRAINT user_goals_activity_level_check
    CHECK (activity_level IN ('light','high','very_high')) NOT VALID;
ALTER TABLE public.user_goals DROP CONSTRAINT IF EXISTS user_goals_nutrition_goal_mode_check;
ALTER TABLE public.user_goals ADD CONSTRAINT user_goals_nutrition_goal_mode_check
    CHECK (nutrition_goal_mode IN ('manual','automatic')) NOT VALID;
ALTER TABLE public.user_goals DROP CONSTRAINT IF EXISTS user_goals_nutrition_targets_check;
ALTER TABLE public.user_goals ADD CONSTRAINT user_goals_nutrition_targets_check
    CHECK (nutrition_goal_mode IS NULL OR (activity_level IS NOT NULL AND daily_calorie_target IS NOT NULL
      AND daily_protein_target_g IS NOT NULL AND daily_carbs_target_g IS NOT NULL AND daily_fat_target_g IS NOT NULL)) NOT VALID;
ALTER TABLE public.user_goals DROP CONSTRAINT IF EXISTS user_goals_automatic_inputs_check;
ALTER TABLE public.user_goals ADD CONSTRAINT user_goals_automatic_inputs_check
    CHECK (nutrition_goal_mode IS DISTINCT FROM 'automatic' OR
      (calculation_weight_kg IS NOT NULL AND estimated_tdee_kcal IS NOT NULL AND calorie_adjustment_pct IS NOT NULL)) NOT VALID;
ALTER TABLE public.user_goals DROP CONSTRAINT IF EXISTS user_goals_macros_nonnegative_check;
ALTER TABLE public.user_goals ADD CONSTRAINT user_goals_macros_nonnegative_check
    CHECK (daily_protein_target_g BETWEEN 0 AND 5000 AND daily_carbs_target_g BETWEEN 0 AND 5000
      AND daily_fat_target_g BETWEEN 0 AND 5000) NOT VALID;
ALTER TABLE public.user_goals DROP CONSTRAINT IF EXISTS user_goals_daily_calorie_target_check;
ALTER TABLE public.user_goals ADD CONSTRAINT user_goals_daily_calorie_target_check
    CHECK (daily_calorie_target BETWEEN 1 AND 20000) NOT VALID;
ALTER TABLE public.user_goals DROP CONSTRAINT IF EXISTS user_goals_calculation_values_check;
ALTER TABLE public.user_goals ADD CONSTRAINT user_goals_calculation_values_check
    CHECK (calculation_weight_kg > 0 AND estimated_tdee_kcal BETWEEN 0.000001 AND 9999999.999999) NOT VALID;

CREATE SCHEMA IF NOT EXISTS private;
REVOKE ALL ON SCHEMA private FROM PUBLIC;
GRANT USAGE ON SCHEMA private TO authenticated, service_role;

-- Retirar la firma anterior para evitar sobrecargas ambiguas en PostgREST.
DROP FUNCTION IF EXISTS public.save_manual_nutrition_goal(text,integer,numeric,numeric,numeric);
DROP FUNCTION IF EXISTS private.insert_manual_nutrition_goal(text,integer,numeric,numeric,numeric);

CREATE OR REPLACE FUNCTION private.insert_manual_nutrition_goal(
    p_goal_type text, p_daily_calorie_target integer,
    p_daily_protein_target_g numeric, p_daily_carbs_target_g numeric,
    p_daily_fat_target_g numeric, p_activity_level text
) RETURNS public.user_goals
LANGUAGE plpgsql SECURITY DEFINER SET search_path TO ''
AS $function$
DECLARE
    v_user_id uuid := auth.uid();
    v_current public.user_goals;
BEGIN
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'Usuario no autenticado';
    END IF;
    IF p_goal_type IS NULL OR p_goal_type NOT IN ('gain_muscle','lose_weight','maintain_weight') THEN
        RAISE EXCEPTION 'Objetivo físico no válido';
    END IF;
    IF p_activity_level IS NULL OR p_activity_level NOT IN ('light','high','very_high') THEN
        RAISE EXCEPTION 'Nivel de actividad no válido';
    END IF;
    IF p_daily_calorie_target IS NULL OR p_daily_calorie_target NOT BETWEEN 1 AND 20000 THEN
        RAISE EXCEPTION 'Meta calórica no válida';
    END IF;
    IF p_daily_protein_target_g IS NULL OR p_daily_carbs_target_g IS NULL OR p_daily_fat_target_g IS NULL
       OR p_daily_protein_target_g NOT BETWEEN 0 AND 5000
       OR p_daily_carbs_target_g NOT BETWEEN 0 AND 5000
       OR p_daily_fat_target_g NOT BETWEEN 0 AND 5000
       OR p_daily_protein_target_g <> round(p_daily_protein_target_g,2)
       OR p_daily_carbs_target_g <> round(p_daily_carbs_target_g,2)
       OR p_daily_fat_target_g <> round(p_daily_fat_target_g,2) THEN
        RAISE EXCEPTION 'Macronutrientes no válidos';
    END IF;

    PERFORM pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(v_user_id::text,0));
    SELECT * INTO v_current FROM public.user_goals WHERE user_id = v_user_id
        ORDER BY effective_at DESC,id DESC LIMIT 1;
    IF v_current.id IS NOT NULL AND v_current.nutrition_goal_mode = 'manual'
       AND ROW(v_current.goal_type,v_current.activity_level,v_current.daily_calorie_target,
               v_current.daily_protein_target_g,v_current.daily_carbs_target_g,v_current.daily_fat_target_g)
       IS NOT DISTINCT FROM ROW(p_goal_type,p_activity_level,p_daily_calorie_target,
               p_daily_protein_target_g,p_daily_carbs_target_g,p_daily_fat_target_g) THEN
        RETURN v_current;
    END IF;

    INSERT INTO public.user_goals(user_id,goal_type,activity_level,nutrition_goal_mode,
        daily_calorie_target,daily_protein_target_g,daily_carbs_target_g,daily_fat_target_g)
    VALUES (v_user_id,p_goal_type,p_activity_level,'manual',
        p_daily_calorie_target,p_daily_protein_target_g,p_daily_carbs_target_g,p_daily_fat_target_g)
    RETURNING * INTO v_current;
    RETURN v_current;
END;
$function$;

CREATE OR REPLACE FUNCTION private.insert_automatic_nutrition_goal(p_user_id uuid, p_goal_type text, p_activity_level text, p_daily_calorie_target integer, p_daily_protein_target_g numeric, p_daily_carbs_target_g numeric, p_daily_fat_target_g numeric, p_calorie_adjustment_pct numeric, p_calculation_weight_kg numeric, p_estimated_tdee_kcal numeric)
 RETURNS public.user_goals
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
DECLARE
    v_current public.user_goals;
BEGIN
    IF p_user_id IS NULL THEN
        RAISE EXCEPTION 'Usuario requerido';
    END IF;

    IF p_goal_type IS NULL OR p_goal_type NOT IN ('gain_muscle', 'lose_weight', 'maintain_weight') THEN
        RAISE EXCEPTION 'Objetivo físico no válido';
    END IF;
    IF p_activity_level IS NULL OR p_activity_level NOT IN ('light','high','very_high') THEN
        RAISE EXCEPTION 'Nivel de actividad no válido';
    END IF;
    IF p_daily_calorie_target IS NULL OR p_daily_calorie_target NOT BETWEEN 1500 AND 5000 THEN
        RAISE EXCEPTION 'Meta calórica automática no válida';
    END IF;
    IF p_daily_protein_target_g IS NULL OR p_daily_carbs_target_g IS NULL OR p_daily_fat_target_g IS NULL
       OR p_daily_protein_target_g NOT BETWEEN 0 AND 5000
       OR p_daily_carbs_target_g NOT BETWEEN 0 AND 5000
       OR p_daily_fat_target_g NOT BETWEEN 0 AND 5000
       OR p_daily_protein_target_g <> round(p_daily_protein_target_g, 2)
       OR p_daily_carbs_target_g <> round(p_daily_carbs_target_g, 2)
       OR p_daily_fat_target_g <> round(p_daily_fat_target_g, 2) THEN
        RAISE EXCEPTION 'Macronutrientes automáticos no válidos';
    END IF;
    IF p_calculation_weight_kg IS NULL OR p_calculation_weight_kg NOT BETWEEN 30 AND 300
       OR p_calculation_weight_kg <> round(p_calculation_weight_kg, 2)
       OR p_estimated_tdee_kcal IS NULL
       OR p_estimated_tdee_kcal NOT BETWEEN 0.000001 AND 9999999.999999
       OR p_estimated_tdee_kcal <> round(p_estimated_tdee_kcal, 6) THEN
        RAISE EXCEPTION 'Datos de cálculo no válidos';
    END IF;
    IF p_calorie_adjustment_pct IS NULL OR p_calorie_adjustment_pct <>
        (CASE p_goal_type WHEN 'gain_muscle' THEN 10 WHEN 'lose_weight' THEN -15 ELSE 0 END) THEN
        RAISE EXCEPTION 'Ajuste calórico no válido';
    END IF;
    IF p_daily_calorie_target <> round(p_estimated_tdee_kcal * (1 + p_calorie_adjustment_pct / 100))
       OR abs(p_daily_protein_target_g * 4 + p_daily_carbs_target_g * 4 +
              p_daily_fat_target_g * 9 - p_daily_calorie_target) > 0.09 THEN
        RAISE EXCEPTION 'Metas automáticas inconsistentes';
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
$function$;

CREATE OR REPLACE FUNCTION public.save_automatic_nutrition_goal(p_user_id uuid, p_goal_type text, p_activity_level text, p_daily_calorie_target integer, p_daily_protein_target_g numeric, p_daily_carbs_target_g numeric, p_daily_fat_target_g numeric, p_calorie_adjustment_pct numeric, p_calculation_weight_kg numeric, p_estimated_tdee_kcal numeric)
 RETURNS public.user_goals
 LANGUAGE sql
 SET search_path TO ''
AS $function$
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
$function$;

CREATE OR REPLACE FUNCTION public.save_manual_nutrition_goal(
    p_goal_type text, p_daily_calorie_target integer,
    p_daily_protein_target_g numeric, p_daily_carbs_target_g numeric,
    p_daily_fat_target_g numeric, p_activity_level text
) RETURNS public.user_goals
LANGUAGE sql SET search_path TO ''
AS $function$
    SELECT private.insert_manual_nutrition_goal(p_goal_type,p_daily_calorie_target,
        p_daily_protein_target_g,p_daily_carbs_target_g,p_daily_fat_target_g,p_activity_level);
$function$;
-- El cliente autenticado guarda mediante RPC; no se habilita INSERT directo.
REVOKE INSERT ON public.user_goals FROM anon, authenticated;
REVOKE ALL ON FUNCTION private.insert_manual_nutrition_goal(text,integer,numeric,numeric,numeric,text) FROM PUBLIC, anon, service_role;
GRANT EXECUTE ON FUNCTION private.insert_manual_nutrition_goal(text,integer,numeric,numeric,numeric,text) TO authenticated;
REVOKE ALL ON FUNCTION public.save_manual_nutrition_goal(text,integer,numeric,numeric,numeric,text) FROM PUBLIC, anon, service_role;
GRANT EXECUTE ON FUNCTION public.save_manual_nutrition_goal(text,integer,numeric,numeric,numeric,text) TO authenticated;
REVOKE ALL ON FUNCTION private.insert_automatic_nutrition_goal(uuid,text,text,integer,numeric,numeric,numeric,numeric,numeric,numeric) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.save_automatic_nutrition_goal(uuid,text,text,integer,numeric,numeric,numeric,numeric,numeric,numeric) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION private.insert_automatic_nutrition_goal(uuid,text,text,integer,numeric,numeric,numeric,numeric,numeric,numeric) TO service_role;
GRANT EXECUTE ON FUNCTION public.save_automatic_nutrition_goal(uuid,text,text,integer,numeric,numeric,numeric,numeric,numeric,numeric) TO service_role;
NOTIFY pgrst, 'reload schema';
COMMIT;
