-- Rotina de uma vez so ("unica"): atividade com data marcada, como um compromisso de calendario.
-- Usa proxima_em como a data/hora. Idempotente.
alter table public.routines drop constraint if exists routines_recorrencia_check;
alter table public.routines add constraint routines_recorrencia_check
  check (recorrencia in ('diaria', 'dias_da_semana', 'a_cada_n_dias', 'a_cada_n_semanas', 'unica'));
