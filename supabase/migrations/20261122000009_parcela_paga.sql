-- Parcela do cartao marcada como paga. Idempotente.
-- O gasto continua contando no ciclo em que foi lancado (foi um custo real); o campo serve para
-- separar o que ainda esta em aberto do que ja foi quitado.
alter table public.finance_entries add column if not exists pago boolean not null default false;
