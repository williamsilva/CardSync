-- A Cielo é seedada como INATIVA (status=2) em V20260516_07__company_acquirer_seed.sql,
-- de antes da importação Cielo existir. Sem esta migration, um reset de banco reverte a
-- ativação manual e trava em cascata as Etapas 1 (ERP x ACQ), 1b, 6 e 7 da esteira de
-- conciliação para todas as transações/ordens/resumos da Cielo.
UPDATE cs_acquirer SET status = 1 WHERE file_identifier = 'Cielo' AND status <> 1;
