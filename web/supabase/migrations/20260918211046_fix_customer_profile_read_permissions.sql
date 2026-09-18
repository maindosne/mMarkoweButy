revoke truncate, references, trigger on public.customer_profiles from anon, authenticated, service_role;
grant select on public.customer_profiles to authenticated, service_role;
