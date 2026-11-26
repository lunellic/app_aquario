-- Diario de diagnostico do app do tablet: quedas e cada passo da atualizacao. Idempotente.
-- O app so consegue INSERIR (e so linhas dele). Ninguem le pelo app: a leitura e feita pelo
-- workflow "Diagnostico do tablet", com a chave de administracao, para entender um "o app fecha"
-- sem depender de print.
create table if not exists public.app_diagnosticos (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  criado_em timestamptz not null default now(),
  recebido_em timestamptz not null default now(),
  aparelho text,
  versao int,
  tipo text not null,
  texto text not null
);
create index if not exists app_diagnosticos_criado on public.app_diagnosticos (criado_em desc);
alter table public.app_diagnosticos enable row level security;
drop policy if exists app_diagnosticos_insere on public.app_diagnosticos;
create policy app_diagnosticos_insere on public.app_diagnosticos
  for insert to authenticated with check (user_id = auth.uid());
-- Guarda so o ultimo mes.
delete from public.app_diagnosticos where criado_em < now() - interval '30 days';
