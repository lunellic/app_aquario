-- Avisos no iPhone pelo PWA (Web Push). Uma linha por aparelho inscrito; o endpoint e unico porque
-- e o endereco que a Apple deu para aquele PWA naquele iPhone. Tabela interna da Edge Function
-- notifier: RLS ligado e nenhuma policy, so a service role le e grava (o PWA passa pela funcao).
-- Idempotente.
create table if not exists public.push_subscriptions (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  endpoint text not null unique,
  p256dh text not null,
  auth text not null,
  aparelho text,
  criado_em timestamptz not null default now(),
  usado_em timestamptz
);
alter table public.push_subscriptions enable row level security;
revoke all on public.push_subscriptions from anon, authenticated;
