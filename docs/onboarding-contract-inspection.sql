-- Inspección del contrato de onboarding. SOLO LECTURA.
-- Ejecutar en el SQL Editor del proyecto Supabase vigente.
-- Devuelve metadatos: no consulta registros de usuarios ni ejecuta RPC.
-- El listado de RPC es orientativo; confirmar la función y su definición con Javier.
WITH target_tables AS (
    SELECT c.oid,
           n.nspname AS table_schema,
           c.relname AS table_name,
           c.relrowsecurity AS rls_enabled,
           c.relforcerowsecurity AS rls_forced
    FROM pg_catalog.pg_class AS c
    JOIN pg_catalog.pg_namespace AS n ON n.oid = c.relnamespace
    WHERE n.nspname = 'public'
      AND c.relname IN ('profiles', 'user_goals', 'body_weight_logs')
      AND c.relkind IN ('r', 'p')
),
column_metadata AS (
    SELECT c.table_schema, c.table_name, c.ordinal_position, c.column_name,
           c.data_type, c.udt_schema, c.udt_name, c.domain_schema, c.domain_name,
           c.is_nullable, c.column_default, c.numeric_precision, c.numeric_scale,
           c.character_maximum_length
    FROM information_schema.columns AS c
    JOIN target_tables AS t
      ON t.table_schema = c.table_schema AND t.table_name = c.table_name
),
constraint_metadata AS (
    SELECT t.table_schema, t.table_name, c.conname AS constraint_name,
           c.contype AS constraint_type, c.convalidated AS validated,
           pg_catalog.pg_get_constraintdef(c.oid, true) AS definition
    FROM pg_catalog.pg_constraint AS c
    JOIN target_tables AS t ON t.oid = c.conrelid
),
enum_metadata AS (
    SELECT c.table_schema, c.table_name, c.column_name,
           e.enumsortorder AS value_order, e.enumlabel AS allowed_value
    FROM column_metadata AS c
    JOIN pg_catalog.pg_namespace AS n ON n.nspname = c.udt_schema
    JOIN pg_catalog.pg_type AS t ON t.typnamespace = n.oid AND t.typname = c.udt_name
    JOIN pg_catalog.pg_enum AS e ON e.enumtypid = t.oid
),
index_metadata AS (
    SELECT i.schemaname AS table_schema, i.tablename AS table_name,
           i.indexname AS index_name, i.indexdef AS definition
    FROM pg_catalog.pg_indexes AS i
    JOIN target_tables AS t
      ON t.table_schema = i.schemaname AND t.table_name = i.tablename
),
policy_metadata AS (
    SELECT p.schemaname AS table_schema, p.tablename AS table_name,
           p.policyname AS policy_name, p.permissive, p.roles,
           p.cmd AS command, p.qual AS using_expression,
           p.with_check AS check_expression
    FROM pg_catalog.pg_policies AS p
    JOIN target_tables AS t
      ON t.table_schema = p.schemaname AND t.table_name = p.tablename
),
table_permissions AS (
    SELECT t.table_schema, t.table_name,
           pg_catalog.has_table_privilege('anon', t.oid, 'SELECT') AS anon_select,
           pg_catalog.has_table_privilege('anon', t.oid, 'INSERT') AS anon_insert,
           pg_catalog.has_table_privilege('anon', t.oid, 'UPDATE') AS anon_update,
           pg_catalog.has_table_privilege('anon', t.oid, 'DELETE') AS anon_delete,
           pg_catalog.has_table_privilege('authenticated', t.oid, 'SELECT') AS authenticated_select,
           pg_catalog.has_table_privilege('authenticated', t.oid, 'INSERT') AS authenticated_insert,
           pg_catalog.has_table_privilege('authenticated', t.oid, 'UPDATE') AS authenticated_update,
           pg_catalog.has_table_privilege('authenticated', t.oid, 'DELETE') AS authenticated_delete
    FROM target_tables AS t
),
trigger_metadata AS (
    SELECT n.nspname AS table_schema, c.relname AS table_name,
           t.tgname AS trigger_name, t.tgenabled AS enabled_mode,
           fn_ns.nspname AS function_schema, fn.proname AS function_name,
           pg_catalog.pg_get_function_identity_arguments(fn.oid) AS function_arguments
    FROM pg_catalog.pg_trigger AS t
    JOIN pg_catalog.pg_class AS c ON c.oid = t.tgrelid
    JOIN pg_catalog.pg_namespace AS n ON n.oid = c.relnamespace
    JOIN pg_catalog.pg_proc AS fn ON fn.oid = t.tgfoid
    JOIN pg_catalog.pg_namespace AS fn_ns ON fn_ns.oid = fn.pronamespace
    WHERE NOT t.tgisinternal
      AND (
          c.oid IN (SELECT oid FROM target_tables)
          OR (n.nspname = 'auth' AND c.relname = 'users')
      )
),
rpc_candidates AS (
    SELECT n.nspname AS function_schema, p.proname AS function_name,
           pg_catalog.pg_get_function_identity_arguments(p.oid) AS identity_arguments,
           p.proargnames AS argument_names,
           p.pronargdefaults AS default_argument_count,
           pg_catalog.pg_get_function_result(p.oid) AS result_type,
           p.prosecdef AS security_definer,
           p.proisstrict AS null_input_skips_execution,
           pg_catalog.has_function_privilege('anon', p.oid, 'EXECUTE') AS anon_execute,
           pg_catalog.has_function_privilege('authenticated', p.oid, 'EXECUTE') AS authenticated_execute,
           (
               SELECT string_agg(setting, ', ')
               FROM unnest(p.proconfig) AS config(setting)
               WHERE setting LIKE 'search_path=%'
           ) AS configured_search_path
    FROM pg_catalog.pg_proc AS p
    JOIN pg_catalog.pg_namespace AS n ON n.oid = p.pronamespace
    WHERE p.prokind = 'f'
      AND n.nspname NOT IN ('pg_catalog', 'information_schema')
      AND n.nspname NOT LIKE 'pg_%'
      AND p.prorettype NOT IN ('pg_catalog.trigger'::regtype, 'pg_catalog.event_trigger'::regtype)
      AND (
          p.proname ILIKE '%onboard%'
          OR p.prosrc ILIKE '%profiles%'
          OR p.prosrc ILIKE '%user_goals%'
          OR p.prosrc ILIKE '%body_weight_logs%'
      )
)
SELECT jsonb_build_object(
    'server_version', current_setting('server_version'),
    'tables', COALESCE((SELECT jsonb_agg(to_jsonb(t) ORDER BY t.table_name) FROM target_tables AS t), '[]'::jsonb),
    'columns', COALESCE((SELECT jsonb_agg(to_jsonb(c) ORDER BY c.table_name, c.ordinal_position) FROM column_metadata AS c), '[]'::jsonb),
    'constraints', COALESCE((SELECT jsonb_agg(to_jsonb(c) ORDER BY c.table_name, c.constraint_name) FROM constraint_metadata AS c), '[]'::jsonb),
    'enum_values', COALESCE((SELECT jsonb_agg(to_jsonb(e) ORDER BY e.table_name, e.column_name, e.value_order) FROM enum_metadata AS e), '[]'::jsonb),
    'indexes', COALESCE((SELECT jsonb_agg(to_jsonb(i) ORDER BY i.table_name, i.index_name) FROM index_metadata AS i), '[]'::jsonb),
    'policies', COALESCE((SELECT jsonb_agg(to_jsonb(p) ORDER BY p.table_name, p.policy_name) FROM policy_metadata AS p), '[]'::jsonb),
    'table_permissions', COALESCE((SELECT jsonb_agg(to_jsonb(p) ORDER BY p.table_name) FROM table_permissions AS p), '[]'::jsonb),
    'triggers', COALESCE((SELECT jsonb_agg(to_jsonb(t) ORDER BY t.table_schema, t.table_name, t.trigger_name) FROM trigger_metadata AS t), '[]'::jsonb),
    'rpc_candidates', COALESCE((SELECT jsonb_agg(to_jsonb(r) ORDER BY r.function_schema, r.function_name) FROM rpc_candidates AS r), '[]'::jsonb)
) AS onboarding_contract_snapshot;
