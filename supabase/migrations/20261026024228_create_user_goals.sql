CREATE TABLE public.user_goals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    user_id UUID NOT NULL
        REFERENCES auth.users(id)
        ON DELETE CASCADE,

    goal_type TEXT NOT NULL
        CHECK (
            goal_type IN (
                'gain_muscle',
                'lose_weight',
                'maintain_weight'
            )
        ),

    daily_calorie_target INTEGER
        CHECK (daily_calorie_target > 0),

    effective_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_user_goals_user_effective
ON public.user_goals (user_id, effective_at DESC);

ALTER TABLE public.user_goals ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Users can view own goals"
ON public.user_goals
FOR SELECT
TO authenticated
USING (
    (SELECT auth.uid()) = user_id
);

CREATE POLICY "Users can insert own goals"
ON public.user_goals
FOR INSERT
TO authenticated
WITH CHECK (
    (SELECT auth.uid()) = user_id
);