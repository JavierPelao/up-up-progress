CREATE TABLE public.routine_exercises (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    routine_id UUID NOT NULL
        REFERENCES public.routines(id)
        ON DELETE CASCADE,

    exercise_id UUID NOT NULL
        REFERENCES public.exercises(id)
        ON DELETE RESTRICT,

    position INTEGER NOT NULL
        CHECK (position > 0),

    planned_sets INTEGER NOT NULL
        CHECK (planned_sets > 0),

    planned_reps INTEGER NOT NULL
        CHECK (planned_reps > 0),

    UNIQUE (routine_id, position)
);

CREATE INDEX idx_routine_exercises_exercise_id
ON public.routine_exercises (exercise_id);

ALTER TABLE public.routine_exercises ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Users can view own routine exercises"
ON public.routine_exercises
FOR SELECT
TO authenticated
USING (
    EXISTS (
        SELECT 1
        FROM public.routines r
        WHERE r.id = routine_id
          AND r.user_id = (SELECT auth.uid())
    )
);

CREATE POLICY "Users can insert own routine exercises"
ON public.routine_exercises
FOR INSERT
TO authenticated
WITH CHECK (
    EXISTS (
        SELECT 1
        FROM public.routines r
        WHERE r.id = routine_id
          AND r.user_id = (SELECT auth.uid())
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

CREATE POLICY "Users can update own routine exercises"
ON public.routine_exercises
FOR UPDATE
TO authenticated
USING (
    EXISTS (
        SELECT 1
        FROM public.routines r
        WHERE r.id = routine_id
          AND r.user_id = (SELECT auth.uid())
    )
)
WITH CHECK (
    EXISTS (
        SELECT 1
        FROM public.routines r
        WHERE r.id = routine_id
          AND r.user_id = (SELECT auth.uid())
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

CREATE POLICY "Users can delete own routine exercises"
ON public.routine_exercises
FOR DELETE
TO authenticated
USING (
    EXISTS (
        SELECT 1
        FROM public.routines r
        WHERE r.id = routine_id
          AND r.user_id = (SELECT auth.uid())
    )
);