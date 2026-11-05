
-- Backend 11.3: datos personales para el calculo nutricional.

-- 1. Agregar atributos al perfil.
-- Son opcionales para mantener compatibilidad
-- con los usuarios que ya existen.

ALTER TABLE public.profiles
    ADD COLUMN age_years SMALLINT,
    ADD COLUMN height_cm NUMERIC(5,1),
    ADD COLUMN sex_for_formula TEXT;

-- 2. Validar edad.
-- Propuesta inicial: calculo automatico para adultos.

ALTER TABLE public.profiles
    ADD CONSTRAINT profiles_age_years_check
    CHECK (
        age_years BETWEEN 18 AND 120
    );

-- 3. Validar estatura en centimetros.

ALTER TABLE public.profiles
    ADD CONSTRAINT profiles_height_cm_check
    CHECK (
        height_cm BETWEEN 100 AND 250
    );

-- 4. Restringir la variante de la formula
-- Mifflin-St Jeor.

ALTER TABLE public.profiles
    ADD CONSTRAINT profiles_sex_for_formula_check
    CHECK (
        sex_for_formula IN ('male', 'female')
    );
