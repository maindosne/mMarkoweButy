-- Keep customer chat data backend-only.
revoke all on table public.chat_conversations from anon, authenticated;
revoke all on table public.chat_messages from anon, authenticated;

grant select, insert, update, delete on table public.chat_conversations to service_role;
grant select, insert, update, delete on table public.chat_messages to service_role;
