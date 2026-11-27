-- Rotina sem horario ("faco quando der"): vale para o dia, sem alarme proprio, e so fica atrasada
-- depois da meia-noite. O horario continua gravado porque compoe a chave dos registros ja feitos.
-- Idempotente.
alter table public.routines add column if not exists com_horario boolean not null default true;
