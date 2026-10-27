CREATE TABLE public.routines (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    user_id UUID NOT NULL
        REFERENCES auth.users(id)
        ON DELETE CASCADE,

    name TEXT NOT NULL
        CHECK (length(trim(name)) > 0),

    description TEXT
);

CREATE INDEX idx_routines_user_id
ON public.routines (user_id);

ALTER TABLE public.routines ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Users can view own routines"
ON public.routines
FOR SELECT
TO authenticated
USING (
    (SELECT auth.uid()) = user_id
);

CREATE POLICY "Users can insert own routines"
ON public.routines
FOR INSERT
TO authenticated
WITH CHECK (
    (SELECT auth.uid()) = user_id
);

CREATE POLICY "Users can update own routines"
ON public.routines
FOR UPDATE
TO authenticated
USING (
    (SELECT auth.uid()) = user_id
)
WITH CHECK (
    (SELECT auth.uid()) = user_id
);

CREATE POLICY "Users can delete own routines"
ON public.routines
FOR DELETE
TO authenticated
USING (
    (SELECT auth.uid()) = user_id
);