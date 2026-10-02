-- Customer AI chat: persistent conversations and messages.
-- Browser clients never access these tables directly; the Edge Function uses service_role.

create table if not exists public.chat_conversations (
  id uuid primary key default gen_random_uuid(),
  client_key_hash text not null,
  customer_user_id uuid null references auth.users(id) on delete set null,
  status text not null default 'ai' check (status in ('ai','waiting_human','human','closed')),
  handoff_requested boolean not null default false,
  metadata jsonb not null default '{}'::jsonb,
  last_message text null,
  last_message_role text null check (last_message_role is null or last_message_role in ('customer','assistant','human','system')),
  started_at timestamptz not null default now(),
  last_message_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.chat_messages (
  id uuid primary key default gen_random_uuid(),
  conversation_id uuid not null references public.chat_conversations(id) on delete cascade,
  role text not null check (role in ('customer','assistant','human','system')),
  content text not null check (char_length(content) between 1 and 5000),
  source text not null default 'web',
  created_at timestamptz not null default now()
);

create index if not exists chat_conversations_client_key_idx
  on public.chat_conversations(client_key_hash, updated_at desc);

create index if not exists chat_conversations_customer_idx
  on public.chat_conversations(customer_user_id, updated_at desc)
  where customer_user_id is not null;

create index if not exists chat_conversations_handoff_idx
  on public.chat_conversations(status, handoff_requested, updated_at desc);

create index if not exists chat_messages_conversation_idx
  on public.chat_messages(conversation_id, created_at asc);

alter table public.chat_conversations enable row level security;
alter table public.chat_messages enable row level security;
