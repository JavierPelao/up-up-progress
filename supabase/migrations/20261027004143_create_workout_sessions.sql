CREATE TABLE public.workout_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    user_id UUID NOT NULL
        REFERENCES auth.users(id)
        ON DELETE CASCADE,

    routine_id UUID
        REFERENCES public.routines(id)
        ON DELETE SET NULL,

    performed_on DATE NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_workout_sessions_user_date
ON public.workout_sessions (user_id, performed_on DESC);

ALTER TABLE public.workout_sessions ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Users can view own workout sessions"
ON public.workout_sessions
FOR SELECT
TO authenticated
USING (
    (SELECT auth.uid()) = user_id
);

CREATE POLICY "Users can insert own workout sessions"
ON public.workout_sessions
FOR INSERT
TO authenticated
WITH CHECK (
    (SELECT auth.uid()) = user_id
    AND (
        routine_id IS NULL
        OR EXISTS (
            SELECT 1
            FROM public.routines r
            WHERE r.id = routine_id
              AND r.user_id = (SELECT auth.uid())
        )
    )
);

CREATE POLICY "Users can update own workout sessions"
ON public.workout_sessions
FOR UPDATE
TO authenticated
USING (
    (SELECT auth.uid()) = user_id
)
WITH CHECK (
    (SELECT auth.uid()) = user_id
    AND (
        routine_id IS NULL
        OR EXISTS (
            SELECT 1
            FROM public.routines r
            WHERE r.id = routine_id
              AND r.user_id = (SELECT auth.uid())
        )
    )
);

CREATE POLICY "Users can delete own workout sessions"
ON public.workout_sessions
FOR DELETE
TO authenticated
USING (
    (SELECT auth.uid()) = user_id
);