CREATE TABLE public.exercises (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    name TEXT NOT NULL
        CHECK (length(trim(name)) > 0),

    created_by UUID
        REFERENCES auth.users(id)
        ON DELETE CASCADE
);

CREATE INDEX idx_exercises_created_by
ON public.exercises (created_by);

ALTER TABLE public.exercises ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Users can view available exercises"
ON public.exercises
FOR SELECT
TO authenticated
USING (
    created_by IS NULL
    OR (SELECT auth.uid()) = created_by
);

CREATE POLICY "Users can insert own exercises"
ON public.exercises
FOR INSERT
TO authenticated
WITH CHECK (
    created_by = (SELECT auth.uid())
);

CREATE POLICY "Users can update own exercises"
ON public.exercises
FOR UPDATE
TO authenticated
USING (
    created_by = (SELECT auth.uid())
)
WITH CHECK (
    created_by = (SELECT auth.uid())
);