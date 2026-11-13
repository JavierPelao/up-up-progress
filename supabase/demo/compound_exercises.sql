-- Aplicado mediante MCP con autorización explícita a up-up-progress-dev
-- (qlphudyqlfxoecusqxsa) el 2026-11-13, verificado a las 10:08 UTC.
-- Verificación: Press Banca conserva su ID; siete ejercicios compuestos añadidos.
-- Cambia solo el catálogo compartido. No se ejecuta automáticamente al abrir la app.
begin;

update public.exercises
set name = 'Press Banca'
where id = 'c50c050e-68a6-4ec6-a46f-8d499787acbc'
  and created_by is null
  and name = 'Press de banca - prueba IA';

insert into public.exercises (name, created_by)
select proposed.name, null
from (values
  ('Sentadilla'),
  ('Peso muerto'),
  ('Press militar'),
  ('Remo con barra'),
  ('Dominadas'),
  ('Fondos en paralelas'),
  ('Prensa de piernas')
) as proposed(name)
where not exists (
  select 1 from public.exercises existing
  where existing.created_by is null
    and lower(trim(existing.name)) = lower(trim(proposed.name))
);

commit;
