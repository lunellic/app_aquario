-- "Parar o toque no tablet" pelo iPhone: o PWA grava a hora do pedido e o tablet, se estiver tocando
-- desde antes disso, para. Idempotente.
alter table public.tank_config add column if not exists parar_toque_em timestamptz;
