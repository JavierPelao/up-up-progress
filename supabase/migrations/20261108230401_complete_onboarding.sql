-- Registro atómico después de autenticar. No recibe contraseñas ni modifica Auth.
BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '60s';

CREATE OR REPLACE FUNCTION private.onboarding_state(p_user_id uuid)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path TO ''
AS $function$
DECLARE
    v_profile public.profiles;
    v_weight public.body_weight_logs;
    v_goal public.user_goals;
    v_complete boolean;
BEGIN
    IF p_user_id IS NULL THEN RAISE EXCEPTION 'Usuario requerido'; END IF;
    SELECT * INTO v_profile FROM public.profiles WHERE user_id = p_user_id;
    SELECT * INTO v_weight FROM public.body_weight_logs WHERE user_id = p_user_id
        ORDER BY measured_on DESC,id DESC LIMIT 1;
    SELECT * INTO v_goal FROM public.user_goals WHERE user_id = p_user_id
        ORDER BY effective_at DESC,id DESC LIMIT 1;
    v_complete := COALESCE(
        v_profile.user_id IS NOT NULL AND nullif(btrim(v_profile.display_name),'') IS NOT NULL
        AND v_profile.age_years BETWEEN 19 AND 120 AND v_profile.height_cm BETWEEN 100 AND 250
        AND v_profile.sex_for_formula IN ('male','female')
        AND v_weight.id IS NOT NULL AND v_weight.weight_kg BETWEEN 0.01 AND 999.99
        AND v_goal.id IS NOT NULL AND v_goal.goal_type IN ('gain_muscle','lose_weight','maintain_weight')
        AND v_goal.activity_level IN ('light','high','very_high')
        AND v_goal.nutrition_goal_mode IN ('manual','automatic')
        AND v_goal.daily_calorie_target BETWEEN 1 AND 20000
        AND v_goal.daily_protein_target_g BETWEEN 0 AND 5000
        AND v_goal.daily_carbs_target_g BETWEEN 0 AND 5000
        AND v_goal.daily_fat_target_g BETWEEN 0 AND 5000
        AND (v_goal.nutrition_goal_mode = 'manual' OR
             (v_goal.daily_calorie_target BETWEEN 1500 AND 5000
              AND v_goal.calculation_weight_kg BETWEEN 30 AND 300
              AND v_goal.estimated_tdee_kcal BETWEEN 0.000001 AND 9999999.999999
              AND v_goal.calorie_adjustment_pct IS NOT NULL)), false);
    RETURN jsonb_build_object(
        'is_complete',v_complete,
        'profile',CASE WHEN v_profile.user_id IS NULL THEN NULL ELSE to_jsonb(v_profile) END,
        'weight',CASE WHEN v_weight.id IS NULL THEN NULL ELSE to_jsonb(v_weight) END,
        'goal',CASE WHEN v_goal.id IS NULL THEN NULL ELSE to_jsonb(v_goal) END);
END;
$function$;

CREATE OR REPLACE FUNCTION public.get_onboarding_state(p_user_id uuid)
RETURNS jsonb LANGUAGE sql SET search_path TO ''
AS $function$
    SELECT private.onboarding_state(p_user_id);
$function$;

CREATE OR REPLACE FUNCTION private.complete_onboarding(
    p_user_id uuid, p_display_name text, p_age_years integer, p_height_cm numeric, p_sex_for_formula text,
    p_measured_on date, p_weight_kg numeric, p_goal_type text, p_activity_level text, p_nutrition_goal_mode text,
    p_daily_calorie_target integer, p_daily_protein_target_g numeric, p_daily_carbs_target_g numeric,
    p_daily_fat_target_g numeric, p_calorie_adjustment_pct numeric, p_calculation_weight_kg numeric,
    p_estimated_tdee_kcal numeric
) RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path TO ''
AS $function$
DECLARE
    v_state jsonb;
    v_goal public.user_goals;
BEGIN
    IF p_user_id IS NULL THEN RAISE EXCEPTION 'Usuario requerido'; END IF;
    PERFORM pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(p_user_id::text,0));
    v_state := private.onboarding_state(p_user_id);
    -- Este endpoint completa el alta; no reemplaza la edición de un perfil ya completo.
    IF (v_state->>'is_complete')::boolean THEN
        RETURN v_state || jsonb_build_object('already_completed',true);
    END IF;

    IF nullif(btrim(p_display_name),'') IS NULL OR p_age_years IS NULL OR p_age_years NOT BETWEEN 19 AND 120
       OR p_height_cm IS NULL OR p_height_cm NOT BETWEEN 100 AND 250 OR p_height_cm <> round(p_height_cm,1)
       OR p_sex_for_formula IS NULL OR p_sex_for_formula NOT IN ('male','female') THEN
        RAISE EXCEPTION 'Datos del perfil no válidos';
    END IF;
    IF p_measured_on IS NULL OR p_weight_kg IS NULL OR p_weight_kg NOT BETWEEN 0.01 AND 999.99
       OR p_weight_kg <> round(p_weight_kg,2) THEN
        RAISE EXCEPTION 'Peso inicial no válido';
    END IF;
    IF p_goal_type IS NULL OR p_goal_type NOT IN ('gain_muscle','lose_weight','maintain_weight')
       OR p_activity_level IS NULL OR p_activity_level NOT IN ('light','high','very_high')
       OR p_nutrition_goal_mode IS NULL OR p_nutrition_goal_mode NOT IN ('manual','automatic') THEN
        RAISE EXCEPTION 'Opciones de configuración no válidas';
    END IF;
    IF p_daily_calorie_target IS NULL OR p_daily_calorie_target NOT BETWEEN 1 AND 20000
       OR p_daily_protein_target_g IS NULL OR p_daily_carbs_target_g IS NULL OR p_daily_fat_target_g IS NULL
       OR p_daily_protein_target_g NOT BETWEEN 0 AND 5000
       OR p_daily_carbs_target_g NOT BETWEEN 0 AND 5000 OR p_daily_fat_target_g NOT BETWEEN 0 AND 5000
       OR p_daily_protein_target_g <> round(p_daily_protein_target_g,2)
       OR p_daily_carbs_target_g <> round(p_daily_carbs_target_g,2)
       OR p_daily_fat_target_g <> round(p_daily_fat_target_g,2) THEN
        RAISE EXCEPTION 'Metas nutricionales no válidas';
    END IF;
    IF p_nutrition_goal_mode = 'automatic' AND
       (p_age_years NOT BETWEEN 19 AND 78 OR p_calculation_weight_kg IS DISTINCT FROM p_weight_kg) THEN
        RAISE EXCEPTION 'Datos del cálculo automático no válidos';
    END IF;

    INSERT INTO public.profiles(user_id,display_name,age_years,height_cm,sex_for_formula)
    VALUES(p_user_id,btrim(p_display_name),p_age_years,p_height_cm,p_sex_for_formula)
    ON CONFLICT(user_id) DO UPDATE SET
        display_name=excluded.display_name,age_years=excluded.age_years,
        height_cm=excluded.height_cm,sex_for_formula=excluded.sex_for_formula;
    INSERT INTO public.body_weight_logs(user_id,measured_on,weight_kg)
    VALUES(p_user_id,p_measured_on,p_weight_kg)
    ON CONFLICT(user_id,measured_on) DO UPDATE SET weight_kg=excluded.weight_kg;

    IF p_nutrition_goal_mode = 'automatic' THEN
        -- La rutina existente verifica límites, precisión, ajuste, energía e idempotencia.
        SELECT * INTO v_goal FROM private.insert_automatic_nutrition_goal(
            p_user_id,p_goal_type,p_activity_level,p_daily_calorie_target,p_daily_protein_target_g,
            p_daily_carbs_target_g,p_daily_fat_target_g,p_calorie_adjustment_pct,p_calculation_weight_kg,p_estimated_tdee_kcal);
    ELSE
        IF p_calorie_adjustment_pct IS NOT NULL OR p_calculation_weight_kg IS NOT NULL OR p_estimated_tdee_kcal IS NOT NULL THEN
            RAISE EXCEPTION 'Una meta manual no recibe estimaciones automáticas';
        END IF;
        INSERT INTO public.user_goals(user_id,goal_type,activity_level,nutrition_goal_mode,
            daily_calorie_target,daily_protein_target_g,daily_carbs_target_g,daily_fat_target_g)
        VALUES(p_user_id,p_goal_type,p_activity_level,'manual',
            p_daily_calorie_target,p_daily_protein_target_g,p_daily_carbs_target_g,p_daily_fat_target_g)
        RETURNING * INTO v_goal;
    END IF;
    v_state := private.onboarding_state(p_user_id);
    IF NOT (v_state->>'is_complete')::boolean THEN
        RAISE EXCEPTION 'No se pudo confirmar la configuración completa';
    END IF;
    RETURN v_state || jsonb_build_object('already_completed',false);
END;
$function$;

CREATE OR REPLACE FUNCTION public.complete_onboarding(
    p_user_id uuid, p_display_name text, p_age_years integer, p_height_cm numeric, p_sex_for_formula text,
    p_measured_on date, p_weight_kg numeric, p_goal_type text, p_activity_level text, p_nutrition_goal_mode text,
    p_daily_calorie_target integer, p_daily_protein_target_g numeric, p_daily_carbs_target_g numeric,
    p_daily_fat_target_g numeric, p_calorie_adjustment_pct numeric, p_calculation_weight_kg numeric,
    p_estimated_tdee_kcal numeric
) RETURNS jsonb LANGUAGE sql SET search_path TO ''
AS $function$
    SELECT private.complete_onboarding(p_user_id,p_display_name,p_age_years,p_height_cm,p_sex_for_formula,
        p_measured_on,p_weight_kg,p_goal_type,p_activity_level,p_nutrition_goal_mode,
        p_daily_calorie_target,p_daily_protein_target_g,p_daily_carbs_target_g,p_daily_fat_target_g,
        p_calorie_adjustment_pct,p_calculation_weight_kg,p_estimated_tdee_kcal);
$function$;

REVOKE ALL ON FUNCTION private.onboarding_state(uuid) FROM PUBLIC,anon,authenticated;
REVOKE ALL ON FUNCTION public.get_onboarding_state(uuid) FROM PUBLIC,anon,authenticated;
REVOKE ALL ON FUNCTION private.complete_onboarding(uuid,text,integer,numeric,text,date,numeric,text,text,text,integer,numeric,numeric,numeric,numeric,numeric,numeric) FROM PUBLIC,anon,authenticated;
REVOKE ALL ON FUNCTION public.complete_onboarding(uuid,text,integer,numeric,text,date,numeric,text,text,text,integer,numeric,numeric,numeric,numeric,numeric,numeric) FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION private.onboarding_state(uuid) TO service_role;
GRANT EXECUTE ON FUNCTION public.get_onboarding_state(uuid) TO service_role;
GRANT EXECUTE ON FUNCTION private.complete_onboarding(uuid,text,integer,numeric,text,date,numeric,text,text,text,integer,numeric,numeric,numeric,numeric,numeric,numeric) TO service_role;
GRANT EXECUTE ON FUNCTION public.complete_onboarding(uuid,text,integer,numeric,text,date,numeric,text,text,text,integer,numeric,numeric,numeric,numeric,numeric,numeric) TO service_role;
NOTIFY pgrst,'reload schema';
COMMIT;
