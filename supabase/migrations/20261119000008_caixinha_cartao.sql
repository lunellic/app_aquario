-- Caixinha do aquario, forma de pagamento (pix sai da caixinha; cartao nao) e parcelas. Idempotente.
alter table public.finance_entries add column if not exists forma text not null default 'pix';
alter table public.finance_entries add column if not exists parcela int;
alter table public.finance_entries add column if not exists parcelas int;
alter table public.finance_entries add column if not exists grupo uuid;
alter table public.finance_entries drop constraint if exists finance_entries_tipo_check;
alter table public.finance_entries add constraint finance_entries_tipo_check check (tipo in ('despesa', 'receita', 'aporte', 'retirada'));
alter table public.finance_entries drop constraint if exists finance_entries_forma_check;
alter table public.finance_entries add constraint finance_entries_forma_check check (forma in ('pix', 'cartao'));
