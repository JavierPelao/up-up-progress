CREATE OR REPLACE FUNCTION public.protect_food_entry_snapshots()
RETURNS TRIGGER
LANGUAGE plpgsql
SET search_path = ''
AS $$
DECLARE
    product_data public.food_products%ROWTYPE;
    refresh_snapshot BOOLEAN := FALSE;
BEGIN
    -- 1. Obtener el producto al que pertenece el consumo.
    SELECT *
    INTO product_data
    FROM public.food_products
    WHERE id = NEW.product_id;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Producto no disponible'
            USING ERRCODE = '23514';
    END IF;

    -- 2. Validar que los gramos/mililitros correspondan.
    IF (
    	NEW.unit = 'g'
    	AND product_data.nutrition_basis = '100ml'
    ) OR (
    	NEW.unit = 'ml'
    	AND product_data.nutrition_basis = '100g'
    ) THEN
    	RAISE EXCEPTION 'Unidad incompatible con el producto'
            USING ERRCODE = '23514';
    END IF;

    -- 3. Proteger los datos provenientes del catálogo.
    IF NEW.nutrition_source = 'catalog' THEN

        -- Sin base nutricional conocida no podemos
        -- interpretar correctamente los valores por 100.
        IF product_data.nutrition_basis IS NULL THEN
            RAISE EXCEPTION 'Producto sin base nutricional definida'
                USING ERRCODE = '23514';
        END IF;

        -- Crear snapshot al insertar un consumo nuevo.
        IF TG_OP = 'INSERT' THEN
            refresh_snapshot := TRUE;

        -- Si cambia el producto o la procedencia,
        -- corresponde generar un snapshot nuevo.
        ELSIF NEW.product_id IS DISTINCT FROM OLD.product_id
           OR NEW.nutrition_source IS DISTINCT FROM OLD.nutrition_source
        THEN
            refresh_snapshot := TRUE;
        END IF;

        IF refresh_snapshot THEN
            -- Los NULL se conservan: no inventamos nutrientes.
            NEW.product_name_snapshot := product_data.name;
            NEW.energy_kcal_100_snapshot := product_data.energy_kcal_100;
            NEW.protein_g_100_snapshot := product_data.protein_g_100;
            NEW.carbs_g_100_snapshot := product_data.carbs_g_100;
            NEW.fat_g_100_snapshot := product_data.fat_g_100;

        ELSE
            -- Si editamos cantidad, fecha o comida,
            -- preservamos los snapshots originales.
            NEW.product_name_snapshot := OLD.product_name_snapshot;
            NEW.energy_kcal_100_snapshot := OLD.energy_kcal_100_snapshot;
            NEW.protein_g_100_snapshot := OLD.protein_g_100_snapshot;
            NEW.carbs_g_100_snapshot := OLD.carbs_g_100_snapshot;
            NEW.fat_g_100_snapshot := OLD.fat_g_100_snapshot;
        END IF;
    END IF;

    -- Los registros user_provisional mantienen sus
    -- valores propios, identificados con esa procedencia.
    RETURN NEW;
END;
$$;


CREATE TRIGGER trg_protect_food_entry_snapshots
BEFORE INSERT OR UPDATE
ON public.food_entries
FOR EACH ROW
EXECUTE FUNCTION public.protect_food_entry_snapshots();