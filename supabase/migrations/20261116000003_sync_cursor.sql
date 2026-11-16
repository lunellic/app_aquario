-- Cursor de sincronizacao: synced_at e carimbado pelo servidor em todo insert/update.
-- O app puxa "tudo com synced_at > ultimo cursor", sem depender do relogio dos aparelhos.
create or replace function public.touch_synced_at() returns trigger
language plpgsql set search_path = '' as $$
begin
  new.synced_at := now();
  return new;
end $$;

do $$
declare t text;
begin
  foreach t in array array[
    'tank_config','param_ranges','water_tests','water_changes','products','equipment','dosings',
    'routines','routine_logs','livestock','species_info','health_cases','health_entries','events',
    'ai_analyses','temperature_readings']
  loop
    execute format('alter table public.%I add column if not exists synced_at timestamptz not null default now()', t);
    execute format('drop trigger if exists touch_synced_at on public.%I', t);
    execute format('create trigger touch_synced_at before insert or update on public.%I for each row execute function public.touch_synced_at()', t);
    execute format('drop index if exists public.%I', t || '_user_sync');
    execute format('create index if not exists %I on public.%I (user_id, synced_at)', t || '_user_synced', t);
  end loop;
end $$;
