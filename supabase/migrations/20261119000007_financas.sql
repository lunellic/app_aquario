-- Financas e lista de compras. Cada item diz para qual aquario foi: marinho, doce ou ambos. Idempotente.
create table if not exists public.finance_entries (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  lancado_em timestamptz not null default now(),
  tipo text not null default 'despesa' check (tipo in ('despesa', 'receita')),
  categoria text not null default 'outros',
  descricao text not null default '',
  valor numeric not null default 0,
  aquario text not null default 'marinho' check (aquario in ('marinho', 'doce', 'ambos')),
  shopping_item_id uuid,
  observacao text
);

create table if not exists public.shopping_items (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  nome text not null default '',
  categoria text not null default 'outros',
  valor_medio numeric,
  prioridade text not null default 'media' check (prioridade in ('alta', 'media', 'baixa')),
  aquario text not null default 'marinho' check (aquario in ('marinho', 'doce', 'ambos')),
  observacao text,
  comprado_em timestamptz,
  valor_pago numeric
);

create index if not exists finance_entries_user_data on public.finance_entries (user_id, lancado_em desc);

do $$
declare t text;
begin
  foreach t in array array['finance_entries', 'shopping_items']
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
