CREATE TABLE public.body_weight_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    user_id UUID NOT NULL
        REFERENCES auth.users(id)
        ON DELETE CASCADE,

    measured_on DATE NOT NULL,

    weight_kg NUMERIC(5,2) NOT NULL
        CHECK (weight_kg > 0),

    UNIQUE (user_id, measured_on)
);

CREATE INDEX idx_body_weight_logs_user_date
ON public.body_weight_logs (user_id, measured_on DESC);

ALTER TABLE public.body_weight_logs ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Users can view own body weight logs"
ON public.body_weight_logs
FOR SELECT
TO authenticated
USING (
    (SELECT auth.uid()) = user_id
);

CREATE POLICY "Users can insert own body weight logs"
ON public.body_weight_logs
FOR INSERT
TO authenticated
WITH CHECK (
    (SELECT auth.uid()) = user_id
);

CREATE POLICY "Users can update own body weight logs"
ON public.body_weight_logs
FOR UPDATE
TO authenticated
USING (
    (SELECT auth.uid()) = user_id
)
WITH CHECK (
    (SELECT auth.uid()) = user_id
);

CREATE POLICY "Users can delete own body weight logs"
ON public.body_weight_logs
FOR DELETE
TO authenticated
USING (
    (SELECT auth.uid()) = user_id
);