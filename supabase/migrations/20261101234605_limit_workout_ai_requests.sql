
-- Backend 08: límite diario de solicitudes de análisis de IA.

CREATE TABLE IF NOT EXISTS public.ai_workout_analysis_usage (
    user_id UUID NOT NULL
        REFERENCES auth.users(id) ON DELETE CASCADE,

    usage_date DATE NOT NULL,

    request_count INTEGER NOT NULL DEFAULT 1
        CHECK (request_count BETWEEN 1 AND 3),

    PRIMARY KEY (user_id, usage_date)
);

-- Ningún usuario puede modificar directamente su contador.
ALTER TABLE public.ai_workout_analysis_usage
    ENABLE ROW LEVEL SECURITY;

REVOKE ALL ON TABLE public.ai_workout_analysis_usage
    FROM anon, authenticated;

-- Esta función consume una solicitud de manera atómica.
-- Devuelve cuántas solicitudes quedan disponibles.
-- Devuelve 0 cuando el límite diario se ha alcanzado.

CREATE OR REPLACE FUNCTION public.claim_workout_analysis_slot()
RETURNS INTEGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_user_id UUID;
    v_count INTEGER;
BEGIN
    v_user_id := auth.uid();

    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'Usuario no autenticado'
            USING ERRCODE = '28000';
    END IF;

    INSERT INTO public.ai_workout_analysis_usage (
        user_id,
        usage_date,
        request_count
    )
    VALUES (
        v_user_id,
        (NOW() AT TIME ZONE 'UTC')::DATE,
        1
    )
    ON CONFLICT (user_id, usage_date)
    DO UPDATE SET
        request_count =
            public.ai_workout_analysis_usage.request_count + 1
    WHERE
        public.ai_workout_analysis_usage.request_count < 3
    RETURNING request_count INTO v_count;

    IF v_count IS NULL THEN
        RETURN -1;
    END IF;

    RETURN 3 - v_count;
END;
$$;


-- Restringir la ejecución a usuarios autenticados.
REVOKE EXECUTE ON FUNCTION
    public.claim_workout_analysis_slot()
    FROM PUBLIC, anon;

GRANT EXECUTE ON FUNCTION
    public.claim_workout_analysis_slot()
    TO authenticated;
