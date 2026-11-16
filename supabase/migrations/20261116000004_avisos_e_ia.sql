-- Bloco C: preferencias de aviso no tank_config, controle de envio de push e limite diario de IA.
alter table public.tank_config add column if not exists ntfy_ativo boolean not null default true;
alter table public.tank_config add column if not exists email_semanal_ativo boolean not null default true;
alter table public.tank_config add column if not exists noite_inicio integer not null default 0 check (noite_inicio between 0 and 23);
alter table public.tank_config add column if not exists noite_fim integer not null default 7 check (noite_fim between 0 and 23);

-- Tabelas internas das Edge Functions: RLS ligado e nenhuma policy = so a service role acessa.
create table if not exists public.notify_log (
  user_id uuid not null references auth.users(id) on delete cascade,
  chave text not null,
  enviado_em timestamptz not null default now(),
  primary key (user_id, chave)
);
alter table public.notify_log enable row level security;
revoke all on public.notify_log from anon, authenticated;

create table if not exists public.ai_usage (
  user_id uuid not null references auth.users(id) on delete cascade,
  dia date not null,
  chamadas integer not null default 0,
  primary key (user_id, dia)
);
alter table public.ai_usage enable row level security;
revoke all on public.ai_usage from anon, authenticated;

-- Conta uma chamada de IA no dia (fuso de Sao Paulo) e devolve o total. Chamada so pela service role.
create or replace function public.bump_ai_usage(p_user uuid) returns integer
language sql security definer set search_path = '' as $$
  insert into public.ai_usage (user_id, dia, chamadas)
  values (p_user, (now() at time zone 'America/Sao_Paulo')::date, 1)
  on conflict (user_id, dia) do update set chamadas = public.ai_usage.chamadas + 1
  returning chamadas;
$$;
revoke all on function public.bump_ai_usage(uuid) from public, anon, authenticated;
