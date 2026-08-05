-- Cielo: cada parcela de uma venda parcelada tem sua PRÓPRIA "Chave UR" (diferente por parcela,
-- não uma chave só pra venda toda — achado real, ver ProcessCielo03Service#buildInstallment).
-- Até aqui, o rvNumber usado no matching de conciliação bancária (BankReconciliationService) e no
-- vínculo Resumo x Ordem (CreditOrderOrphanLinkingService) vinha só de TransactionAcqEntity.rvNumber
-- (um valor só pra venda inteira) — insuficiente pra achar a ordem/lançamento certo de uma parcela
-- específica. Esta coluna guarda o rvNumber DA PARCELA; fica NULL pra Rede (que não tem esse
-- problema — nunca setado, cai no fallback pro rvNumber da transação).
ALTER TABLE cs_installment_acq ADD COLUMN rv_number INT NULL;
CREATE INDEX idx_cs_installment_acq_rv_number ON cs_installment_acq(rv_number);
