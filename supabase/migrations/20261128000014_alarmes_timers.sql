-- Alarmes e timers do tablet passam a sincronizar, para o app do iPhone ver, criar, editar e
-- cancelar. Quem toca continua sendo o tablet; o iPhone recebe o aviso se a chave estiver ligada.
-- Idempotente.
create table if not exists public.alarms (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  titulo text not null default '',
  hora int not null default 7 check (hora between 0 and 23),
  minuto int not null default 0 check (minuto between 0 and 59),
  recorrencia text not null default 'unica' check (recorrencia in ('unica', 'diaria', 'dias_da_semana')),
  dias_semana int[],
  alto boolean not null default true,
  ativo boolean not null default true,
  dia_unico timestamptz,
  avisar_iphone boolean not null default false
);

create table if not exists public.timers (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  label text not null default '',
  termina_em timestamptz not null,
  iphone boolean not null default true,
  encerrado_em timestamptz
);

do $$
declare t text;
begin
  foreach t in array array['alarms', 'timers']
  loop
    execute format('alter table public.%I enable row level security', t);
    execute format('drop policy if exists %I on public.%I', t || '_dono', t);
    execute format(
      'create policy %I on public.%I for all to authenticated using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()))',
      t || '_dono', t);
    execute format('revoke all on public.%I from anon', t);
    execute format('grant select, insert, update, delete on public.%I to authenticated', t);
    execute format('alter table public.%I add column if not exists synced_at timestamptz not null default now()', t);
    execute format('drop trigger if exists touch_synced_at on public.%I', t);
    execute format('create trigger touch_synced_at before insert or update on public.%I for each row execute function public.touch_synced_at()', t);
    execute format('create index if not exists %I on public.%I (user_id, synced_at)', t || '_user_synced', t);
  end loop;
end $$;
