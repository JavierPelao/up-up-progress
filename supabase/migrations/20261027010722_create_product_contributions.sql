CREATE TABLE public.product_contributions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    product_id UUID NOT NULL
        REFERENCES public.food_products(id)
        ON DELETE RESTRICT,

    user_id UUID NOT NULL
        REFERENCES auth.users(id)
        ON DELETE CASCADE,

    payload JSONB NOT NULL
        CHECK (jsonb_typeof(payload) = 'object'),

    status TEXT NOT NULL
        DEFAULT 'pending_review'
        CHECK (
            status IN (
                'pending_review',
                'rejected',
                'approved_local',
                'pending_send',
                'submitted',
                'failed'
            )
        ),

    evidence_path TEXT,

    reviewed_by UUID
        REFERENCES auth.users(id)
        ON DELETE SET NULL,

    reviewed_at TIMESTAMPTZ,

    review_note TEXT,

    attempt_count INTEGER NOT NULL
        DEFAULT 0
        CHECK (attempt_count >= 0),

    last_attempt_at TIMESTAMPTZ,

    last_error TEXT,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_product_contributions_user_date
ON public.product_contributions (user_id, created_at DESC);

CREATE INDEX idx_product_contributions_status_date
ON public.product_contributions (status, created_at);

CREATE INDEX idx_product_contributions_product_id
ON public.product_contributions (product_id);

ALTER TABLE public.product_contributions ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Users can view own product contributions"
ON public.product_contributions
FOR SELECT
TO authenticated
USING (
    (SELECT auth.uid()) = user_id
);

CREATE POLICY "Users can submit own product contributions"
ON public.product_contributions
FOR INSERT
TO authenticated
WITH CHECK (
    user_id = (SELECT auth.uid())

    AND status = 'pending_review'

    AND reviewed_by IS NULL
    AND reviewed_at IS NULL
    AND review_note IS NULL

    AND attempt_count = 0
    AND last_attempt_at IS NULL
    AND last_error IS NULL

    AND EXISTS (
        SELECT 1
        FROM public.food_products p
        WHERE p.id = product_id
          AND (
              p.visibility_status = 'published'
              OR p.created_by = (SELECT auth.uid())
          )
    )
);