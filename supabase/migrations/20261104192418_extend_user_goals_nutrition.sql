
-- Backend 11: ampliar objetivos nutricionales.

-- 1. Nuevos atributos.
-- Se dejan NULL para conservar compatibilidad
-- con los objetivos creados anteriormente.
ALTER TABLE public.user_goals
    ADD COLUMN activity_level TEXT,
    ADD COLUMN nutrition_goal_mode TEXT,
    ADD COLUMN daily_protein_target_g NUMERIC(7,1),
    ADD COLUMN daily_carbs_target_g NUMERIC(7,1),
    ADD COLUMN daily_fat_target_g NUMERIC(7,1),
    ADD COLUMN calorie_adjustment_pct NUMERIC(5,2),
    ADD COLUMN calculation_weight_kg NUMERIC(6,2),
    ADD COLUMN estimated_tdee_kcal NUMERIC(8,1);

-- 2. Ampliar los objetivos fisicos permitidos.
ALTER TABLE public.user_goals
    DROP CONSTRAINT user_goals_goal_type_check;

ALTER TABLE public.user_goals
    ADD CONSTRAINT user_goals_goal_type_check
    CHECK (
        goal_type IN (
            'gain_muscle',
            'lose_weight',
            'maintain_weight',
            'body_recomposition'
        )
    );

-- 3. Validar niveles de actividad.
ALTER TABLE public.user_goals
    ADD CONSTRAINT user_goals_activity_level_check
    CHECK (
        activity_level IN (
            'sedentary',
            'light',
            'moderate',
            'high',
            'very_high'
        )
    );

-- 4. Validar modos nutricionales.
ALTER TABLE public.user_goals
    ADD CONSTRAINT user_goals_nutrition_goal_mode_check
    CHECK (
        nutrition_goal_mode IN ('manual', 'automatic')
    );

-- 5. Evitar cantidades negativas.
ALTER TABLE public.user_goals
    ADD CONSTRAINT user_goals_macros_nonnegative_check
    CHECK (
        daily_protein_target_g >= 0
        AND daily_carbs_target_g >= 0
        AND daily_fat_target_g >= 0
    );

-- 6. Validar los datos usados en el calculo.
ALTER TABLE public.user_goals
    ADD CONSTRAINT user_goals_calculation_values_check
    CHECK (
        calculation_weight_kg > 0
        AND estimated_tdee_kcal > 0
    );

-- 7. Si se configura un modo nuevo,
-- se deben guardar las cuatro metas.
ALTER TABLE public.user_goals
    ADD CONSTRAINT user_goals_nutrition_targets_check
    CHECK (
        nutrition_goal_mode IS NULL
        OR (
            daily_calorie_target IS NOT NULL
            AND daily_protein_target_g IS NOT NULL
            AND daily_carbs_target_g IS NOT NULL
            AND daily_fat_target_g IS NOT NULL
        )
    );

-- 8. El modo automatico requiere los
-- datos que justifican el calculo.
ALTER TABLE public.user_goals
    ADD CONSTRAINT user_goals_automatic_inputs_check
    CHECK (
        nutrition_goal_mode IS DISTINCT FROM 'automatic'
        OR (
            activity_level IS NOT NULL
            AND calculation_weight_kg IS NOT NULL
            AND estimated_tdee_kcal IS NOT NULL
            AND calorie_adjustment_pct IS NOT NULL
        )
    );
