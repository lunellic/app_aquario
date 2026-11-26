-- "Pagou com": alem do Pix da caixinha e do cartao, Pix da conta principal. Idempotente.
-- Pix da conta conta como gasto do ciclo, mas nao tira da caixinha nem fica "a pagar": e o caso de
-- a caixinha acabar e uma compra sair do dinheiro do dia a dia.
alter table public.finance_entries drop constraint if exists finance_entries_forma_check;
alter table public.finance_entries add constraint finance_entries_forma_check check (forma in ('pix', 'pix_conta', 'cartao'));
