-- Vincula a Antecipação à CreditOrder sintética gerada a partir dela (Etapa 4 —
-- SalesSummaryCreditOrderReconciliationService.generateSyntheticOrdersFromAnticipations).
-- Sem isso, a tela de Antecipação não tinha como mostrar se o valor antecipado já foi
-- confirmado no banco (statusPaymentBank vive só na CreditOrder, sem nenhuma referência de
-- volta pra Anticipation que a originou) — achado real 2026-09-10.
alter table cs_anticipation
  add column credit_order_id uuid null;

alter table cs_anticipation
  add constraint fk_cs_anticipation_credit_order
  foreign key (credit_order_id) references cs_credit_order (id) on update cascade;

create index idx_cs_anticipation_credit_order_id on cs_anticipation (credit_order_id);
