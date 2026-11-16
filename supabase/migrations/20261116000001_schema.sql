-- Luluco Reef - esquema v1 (spec secao 5)
-- Convencoes: id uuid gerado no app, user_id default auth.uid(), exclusao logica (deleted_at),
-- updated_at definido pelo cliente (na sincronizacao vence o mais recente).

create extension if not exists pgcrypto;

create table if not exists public.tank_config (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  nome text not null default 'Luluco Reef',
  volume_dosagem_l numeric not null default 100 check (volume_dosagem_l > 0),
  sal_atual text,
  montado_em date,
  foto_vitrine_path text,
  observacoes text
);

create table if not exists public.param_ranges (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  parametro text not null check (parametro in ('kh','ca','mg','salinidade','temperatura','no3','po4')),
  ideal_min numeric not null,
  ideal_max numeric not null,
  alerta_min numeric not null,
  alerta_max numeric not null,
  unidade text not null,
  unique (user_id, parametro)
);

create table if not exists public.water_tests (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  medido_em timestamptz not null,
  parametro text not null check (parametro in ('kh','ca','mg','salinidade','temperatura','no3','po4')),
  valor numeric not null,
  unidade text not null,
  kit text,
  observacao text
);

create table if not exists public.water_changes (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  feita_em timestamptz not null,
  litros numeric not null check (litros > 0),
  sal text,
  salinidade_nova_agua numeric,
  temperatura_nova_agua numeric,
  metodo text,
  observacao text
);

create table if not exists public.products (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  nome text not null,
  marca text,
  tipo text not null default 'suplemento' check (tipo in ('suplemento','aditivo','tratamento','midia_filtrante','outro')),
  dose_quantidade numeric,
  dose_unidade text check (dose_unidade in ('gotas','ml','g','colher','outra')),
  dose_base_litros numeric check (dose_base_litros > 0),
  observacao text,
  ativo boolean not null default true
);

create table if not exists public.equipment (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  tipo text not null default 'outro' check (tipo in ('skimmer','bomba_circulacao','bomba_retorno','luminaria','termostato','osmose','outro')),
  marca text,
  modelo text,
  instalado_em date,
  configuracao text,
  observacao text,
  ativo boolean not null default true
);

create table if not exists public.dosings (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  dosado_em timestamptz not null,
  product_id uuid not null references public.products(id),
  quantidade numeric,
  unidade text,
  observacao text
);

create table if not exists public.routines (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  titulo text not null,
  tipo text not null default 'outro' check (tipo in ('dosagem','tpa','teste','manutencao','outro')),
  product_id uuid references public.products(id),
  equipment_id uuid references public.equipment(id),
  recorrencia text not null check (recorrencia in ('diaria','dias_da_semana','a_cada_n_dias','a_cada_n_semanas')),
  dias_semana smallint[],          -- ISO: 1=segunda ... 7=domingo (para dias_da_semana e a_cada_n_semanas)
  intervalo_n integer check (intervalo_n > 0), -- para a_cada_n_dias / a_cada_n_semanas
  horario time not null,           -- horario local (America/Sao_Paulo)
  proxima_em timestamptz,
  instrucao text,
  ativa boolean not null default true
);

create table if not exists public.routine_logs (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  routine_id uuid not null references public.routines(id),
  previsto_para timestamptz not null,
  status text not null check (status in ('feito','pulado','adiado')),
  feito_em timestamptz,
  observacao text
);

create table if not exists public.livestock (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  tipo text not null check (tipo in ('coral','peixe','invertebrado','anemona','alga')),
  nome_popular text not null,
  nome_cientifico text,
  categoria text check (categoria in ('LPS','SPS','mole','zoa','outro')),
  quantidade integer not null default 1 check (quantidade >= 0),
  local_no_aquario text,
  adicionado_em date,
  origem text,
  status text not null default 'ativo' check (status in ('ativo','removido','morto')),
  removido_em date,
  revisar boolean not null default false,
  observacao text,
  foto_path text
);

create table if not exists public.species_info (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  livestock_id uuid not null references public.livestock(id),
  conteudo_json jsonb not null,
  gerada_em timestamptz not null default now(),
  confirmada_pelo_usuario boolean not null default false
);

create table if not exists public.health_cases (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  livestock_id uuid references public.livestock(id), -- nulo = aquario todo
  tipo text not null check (tipo in ('observacao','quarentena','tratamento')),
  titulo text not null,
  aberto_em timestamptz not null,
  fechado_em timestamptz,
  desfecho text check (desfecho in ('recuperado','morreu','transferido','sem_alteracao')),
  observacao text
);

create table if not exists public.health_entries (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  case_id uuid not null references public.health_cases(id),
  registrado_em timestamptz not null,
  texto text,
  foto_path text,
  product_id uuid references public.products(id)
);

create table if not exists public.events (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  ocorrido_em timestamptz not null,
  tipo text not null default 'observacao' check (tipo in ('observacao','problema','equipamento','alimentacao','outro')),
  texto text not null
);

create table if not exists public.ai_analyses (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  gerada_em timestamptz not null default now(),
  gatilho text not null check (gatilho in ('manual','semanal','especie','saude','chat')),
  pergunta text,
  texto text not null
);

create table if not exists public.temperature_readings (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  lido_em timestamptz not null,
  valor_c numeric not null,
  fonte text
);

-- Indices: user_id + data de cada tabela de registro, e user_id + updated_at para o pull da sincronizacao.
create index if not exists water_tests_user_data on public.water_tests (user_id, medido_em desc);
create index if not exists water_changes_user_data on public.water_changes (user_id, feita_em desc);
create index if not exists dosings_user_data on public.dosings (user_id, dosado_em desc);
create index if not exists routine_logs_user_data on public.routine_logs (user_id, previsto_para desc);
create index if not exists health_cases_user_data on public.health_cases (user_id, aberto_em desc);
create index if not exists health_entries_user_data on public.health_entries (user_id, registrado_em desc);
create index if not exists events_user_data on public.events (user_id, ocorrido_em desc);
create index if not exists ai_analyses_user_data on public.ai_analyses (user_id, gerada_em desc);
create index if not exists temperature_readings_user_data on public.temperature_readings (user_id, lido_em desc);
create index if not exists routines_user_proxima on public.routines (user_id, proxima_em);

-- RLS, grants e indice de sincronizacao em todas as tabelas.
do $$
declare t text;
begin
  foreach t in array array[
    'tank_config','param_ranges','water_tests','water_changes','products','equipment','dosings',
    'routines','routine_logs','livestock','species_info','health_cases','health_entries','events',
    'ai_analyses','temperature_readings']
  loop
    execute format('alter table public.%I enable row level security', t);
    execute format('drop policy if exists %I on public.%I', t || '_dono', t);
    execute format(
      'create policy %I on public.%I for all to authenticated using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()))',
      t || '_dono', t);
    execute format('revoke all on public.%I from anon', t);
    execute format('grant select, insert, update, delete on public.%I to authenticated', t);
    execute format('create index if not exists %I on public.%I (user_id, updated_at)', t || '_user_sync', t);
  end loop;
end $$;

-- Storage: bucket privado de fotos; cada usuario so acessa a pasta com o proprio id (fotos/<user_id>/...).
insert into storage.buckets (id, name, public) values ('fotos', 'fotos', false)
on conflict (id) do nothing;

drop policy if exists fotos_dono_select on storage.objects;
drop policy if exists fotos_dono_insert on storage.objects;
drop policy if exists fotos_dono_update on storage.objects;
drop policy if exists fotos_dono_delete on storage.objects;
create policy fotos_dono_select on storage.objects for select to authenticated
  using (bucket_id = 'fotos' and (storage.foldername(name))[1] = (select auth.uid())::text);
create policy fotos_dono_insert on storage.objects for insert to authenticated
  with check (bucket_id = 'fotos' and (storage.foldername(name))[1] = (select auth.uid())::text);
create policy fotos_dono_update on storage.objects for update to authenticated
  using (bucket_id = 'fotos' and (storage.foldername(name))[1] = (select auth.uid())::text)
  with check (bucket_id = 'fotos' and (storage.foldername(name))[1] = (select auth.uid())::text);
create policy fotos_dono_delete on storage.objects for delete to authenticated
  using (bucket_id = 'fotos' and (storage.foldername(name))[1] = (select auth.uid())::text);
