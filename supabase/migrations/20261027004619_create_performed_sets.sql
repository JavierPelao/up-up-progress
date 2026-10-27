CREATE TABLE public.performed_sets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    workout_exercise_id UUID NOT NULL
        REFERENCES public.workout_exercises(id)
        ON DELETE CASCADE,

    set_number INTEGER NOT NULL
        CHECK (set_number > 0),

    weight_kg NUMERIC(7,2) NOT NULL
        CHECK (weight_kg >= 0),

    reps INTEGER NOT NULL
        CHECK (reps > 0),

    UNIQUE (workout_exercise_id, set_number)
);

ALTER TABLE public.performed_sets ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Users can view own performed sets"
ON public.performed_sets
FOR SELECT
TO authenticated
USING (
    EXISTS (
        SELECT 1
        FROM public.workout_exercises we
        JOIN public.workout_sessions ws
            ON ws.id = we.session_id
        WHERE we.id = workout_exercise_id
          AND ws.user_id = (SELECT auth.uid())
    )
);

CREATE POLICY "Users can insert own performed sets"
ON public.performed_sets
FOR INSERT
TO authenticated
WITH CHECK (
    EXISTS (
        SELECT 1
        FROM public.workout_exercises we
        JOIN public.workout_sessions ws
            ON ws.id = we.session_id
        WHERE we.id = workout_exercise_id
          AND ws.user_id = (SELECT auth.uid())
    )
);

CREATE POLICY "Users can update own performed sets"
ON public.performed_sets
FOR UPDATE
TO authenticated
USING (
    EXISTS (
        SELECT 1
        FROM public.workout_exercises we
        JOIN public.workout_sessions ws
            ON ws.id = we.session_id
        WHERE we.id = workout_exercise_id
          AND ws.user_id = (SELECT auth.uid())
    )
)
WITH CHECK (
    EXISTS (
        SELECT 1
        FROM public.workout_exercises we
        JOIN public.workout_sessions ws
            ON ws.id = we.session_id
        WHERE we.id = workout_exercise_id
          AND ws.user_id = (SELECT auth.uid())
    )
);

CREATE POLICY "Users can delete own performed sets"
ON public.performed_sets
FOR DELETE
TO authenticated
USING (
    EXISTS (
        SELECT 1
        FROM public.workout_exercises we
        JOIN public.workout_sessions ws
            ON ws.id = we.session_id
        WHERE we.id = workout_exercise_id
          AND ws.user_id = (SELECT auth.uid())
    )
);