CREATE TABLE public.food_products (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    barcode TEXT UNIQUE
        CHECK (barcode IS NULL OR length(trim(barcode)) > 0),

    name TEXT NOT NULL
        CHECK (length(trim(name)) > 0),

    brand TEXT,

    energy_kcal_100 NUMERIC(10,2)
        CHECK (energy_kcal_100 >= 0),

    protein_g_100 NUMERIC(10,2)
        CHECK (protein_g_100 >= 0),

    carbs_g_100 NUMERIC(10,2)
        CHECK (carbs_g_100 >= 0),

    fat_g_100 NUMERIC(10,2)
        CHECK (fat_g_100 >= 0),

    nutrition_basis TEXT
        CHECK (nutrition_basis IN ('100g', '100ml')),

    serving_quantity NUMERIC(10,2)
        CHECK (serving_quantity > 0),

    serving_unit TEXT,

    source TEXT NOT NULL
        CHECK (source IN ('open_food_facts', 'manual')),

    visibility_status TEXT NOT NULL
        DEFAULT 'pending_review'
        CHECK (
            visibility_status IN ('published', 'pending_review')
        ),

    created_by UUID
        REFERENCES auth.users(id)
        ON DELETE SET NULL,

    last_synced_at TIMESTAMPTZ
);

CREATE INDEX idx_food_products_name
ON public.food_products (name);

CREATE INDEX idx_food_products_created_by
ON public.food_products (created_by);

ALTER TABLE public.food_products ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Users can view available food products"
ON public.food_products
FOR SELECT
TO authenticated
USING (
    visibility_status = 'published'
    OR created_by = (SELECT auth.uid())
);

CREATE POLICY "Users can insert pending manual products"
ON public.food_products
FOR INSERT
TO authenticated
WITH CHECK (
    created_by = (SELECT auth.uid())
    AND source = 'manual'
    AND visibility_status = 'pending_review'
    AND last_synced_at IS NULL
);