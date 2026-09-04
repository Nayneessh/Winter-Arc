-- PostgREST exposes any function in the `public` schema as an RPC endpoint, which left these
-- two SECURITY DEFINER trigger functions callable by both the anon and authenticated roles.
-- Triggers invoke them through the table owner regardless of these grants, so revoking EXECUTE
-- closes the RPC surface without affecting their actual job.
--
-- Reported by the Supabase security advisor as:
--   0028_anon_security_definer_function_executable
--   0029_authenticated_security_definer_function_executable

revoke all on function public.touch_updated_at() from public, anon, authenticated;
revoke all on function public.handle_new_user() from public, anon, authenticated;
