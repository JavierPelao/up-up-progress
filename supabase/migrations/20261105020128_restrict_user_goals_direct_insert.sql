-- Impedir que los usuarios creen metas directamente.
-- Las escrituras deberán pasar por las RPC autorizadas.
REVOKE INSERT ON TABLE public.user_goals
FROM PUBLIC, anon, authenticated;