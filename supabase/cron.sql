-- Agendamentos do notifier (aplicado pelo workflow de backend, que troca __FUNCTIONS_URL__ e __CRON_SECRET__).
-- A cada 5 minutos: rotinas atrasadas e alertas criticos. Domingo 10h de Brasilia (13h UTC): e-mail semanal.
create extension if not exists pg_cron;
create extension if not exists pg_net;

select cron.unschedule(jobname) from cron.job where jobname in ('luluco_tick', 'luluco_weekly');

select cron.schedule('luluco_tick', '*/5 * * * *', $job$
  select net.http_post(
    url := '__FUNCTIONS_URL__/notifier',
    headers := '{"Content-Type":"application/json","x-cron-secret":"__CRON_SECRET__"}'::jsonb,
    body := '{"mode":"tick"}'::jsonb,
    timeout_milliseconds := 20000);
$job$);

select cron.schedule('luluco_weekly', '0 13 * * 0', $job$
  select net.http_post(
    url := '__FUNCTIONS_URL__/notifier',
    headers := '{"Content-Type":"application/json","x-cron-secret":"__CRON_SECRET__"}'::jsonb,
    body := '{"mode":"weekly"}'::jsonb,
    timeout_milliseconds := 60000);
$job$);
