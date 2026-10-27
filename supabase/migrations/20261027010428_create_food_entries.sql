CREATE TABLE public.food_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    user_id UUID NOT NULL
        REFERENCES auth.users(id)
        ON DELETE CASCADE,

    product_id UUID NOT NULL
        REFERENCES public.food_products(id)
        ON DELETE RESTRICT,

    consumed_on DATE NOT NULL,

    meal_type TEXT NOT NULL
        CHECK (
            meal_type IN (
                'breakfast',
                'lunch',
                'dinner',
                'snack'
            )
        ),

    quantity NUMERIC(10,2) NOT NULL
        CHECK (quantity > 0),

    unit TEXT NOT NULL
        CHECK (unit IN ('g', 'ml')),

    product_name_snapshot TEXT NOT NULL
        CHECK (length(trim(product_name_snapshot)) > 0),

    energy_kcal_100_snapshot NUMERIC(10,2)
        CHECK (energy_kcal_100_snapshot >= 0),

    protein_g_100_snapshot NUMERIC(10,2)
        CHECK (protein_g_100_snapshot >= 0),

    carbs_g_100_snapshot NUMERIC(10,2)
        CHECK (carbs_g_100_snapshot >= 0),

    fat_g_100_snapshot NUMERIC(10,2)
        CHECK (fat_g_100_snapshot >= 0),

    nutrition_source TEXT NOT NULL
        CHECK (
            nutrition_source IN (
                'catalog',
                'user_provisional'
            )
        ),

    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_food_entries_user_date
ON public.food_entries (user_id, consumed_on DESC);

CREATE INDEX idx_food_entries_product_id
ON public.food_entries (product_id);

ALTER TABLE public.food_entries ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Users can view own food entries"
ON public.food_entries
FOR SELECT
TO authenticated
USING (
    (SELECT auth.uid()) = user_id
);

CREATE POLICY "Users can insert own food entries"
ON public.food_entries
FOR INSERT
TO authenticated
WITH CHECK (
    (SELECT auth.uid()) = user_id
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

CREATE POLICY "Users can update own food entries"
ON public.food_entries
FOR UPDATE
TO authenticated
USING (
    (SELECT auth.uid()) = user_id
)
WITH CHECK (
    (SELECT auth.uid()) = user_id
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

CREATE POLICY "Users can delete own food entries"
ON public.food_entries
FOR DELETE
TO authenticated
USING (
    (SELECT auth.uid()) = user_id
);