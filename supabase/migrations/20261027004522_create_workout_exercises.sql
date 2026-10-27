CREATE TABLE public.workout_exercises (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    session_id UUID NOT NULL
        REFERENCES public.workout_sessions(id)
        ON DELETE CASCADE,

    exercise_id UUID NOT NULL
        REFERENCES public.exercises(id)
        ON DELETE RESTRICT,

    position INTEGER NOT NULL
        CHECK (position > 0),

    UNIQUE (session_id, position)
);

CREATE INDEX idx_workout_exercises_exercise_id
ON public.workout_exercises (exercise_id);

ALTER TABLE public.workout_exercises ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Users can view own workout exercises"
ON public.workout_exercises
FOR SELECT
TO authenticated
USING (
    EXISTS (
        SELECT 1
        FROM public.workout_sessions s
        WHERE s.id = session_id
          AND s.user_id = (SELECT auth.uid())
    )
);

CREATE POLICY "Users can insert own workout exercises"
ON public.workout_exercises
FOR INSERT
TO authenticated
WITH CHECK (
    EXISTS (
        SELECT 1
        FROM public.workout_sessions s
        WHERE s.id = session_id
          AND s.user_id = (SELECT auth.uid())
    )
    AND EXISTS (
        SELECT 1
        FROM public.exercises e
        WHERE e.id = exercise_id
          AND (
              e.created_by IS NULL
              OR e.created_by = (SELECT auth.uid())
          )
    )
);

CREATE POLICY "Users can update own workout exercises"
ON public.workout_exercises
FOR UPDATE
TO authenticated
USING (
    EXISTS (
        SELECT 1
        FROM public.workout_sessions s
        WHERE s.id = session_id
          AND s.user_id = (SELECT auth.uid())
    )
)
WITH CHECK (
    EXISTS (
        SELECT 1
        FROM public.workout_sessions s
        WHERE s.id = session_id
          AND s.user_id = (SELECT auth.uid())
    )
    AND EXISTS (
        SELECT 1
        FROM public.exercises e
        WHERE e.id = exercise_id
          AND (
              e.created_by IS NULL
              OR e.created_by = (SELECT auth.uid())
          )
    )
);

CREATE POLICY "Users can delete own workout exercises"
ON public.workout_exercises
FOR DELETE
TO authenticated
USING (
    EXISTS (
        SELECT 1
        FROM public.workout_sessions s
        WHERE s.id = session_id
          AND s.user_id = (SELECT auth.uid())
    )
);