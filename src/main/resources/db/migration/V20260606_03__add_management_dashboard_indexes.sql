-- =====================================================================
-- Índices para acelerar o dashboard de gerenciamento.
-- MySQL 8 / InnoDB
--
-- O dashboard agrega cs_transaction_acq (vendas/taxas), cs_credit_order
-- (pagamentos) e cs_adjustment (débitos), filtrando por company/acquirer/flag
-- e por faixa de data, agrupando por uma dessas dimensões.
--
-- Sem índices compostos, cada agregação faz full scan + filesort sobre
-- centenas de milhares de linhas — daí a lentidão (e o "carregando eterno"
-- no agrupamento por data, que ainda usa função sobre a data).
--
-- Idempotente: cria cada índice apenas se ainda não existir (sem DELIMITER).
-- =====================================================================

-- cs_transaction_acq: filtro por data + dimensões de agrupamento
SET @idx := (SELECT COUNT(1) FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'cs_transaction_acq'
    AND index_name = 'idx_cs_tx_acq_dash_date');
SET @ddl := IF(@idx = 0,
  'CREATE INDEX idx_cs_tx_acq_dash_date ON cs_transaction_acq (sale_date, company_id, acquirer_id, flag_id)',
  'SELECT 1');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @idx := (SELECT COUNT(1) FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'cs_transaction_acq'
    AND index_name = 'idx_cs_tx_acq_dash_company');
SET @ddl := IF(@idx = 0,
  'CREATE INDEX idx_cs_tx_acq_dash_company ON cs_transaction_acq (company_id, sale_date)',
  'SELECT 1');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @idx := (SELECT COUNT(1) FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'cs_transaction_acq'
    AND index_name = 'idx_cs_tx_acq_dash_acquirer');
SET @ddl := IF(@idx = 0,
  'CREATE INDEX idx_cs_tx_acq_dash_acquirer ON cs_transaction_acq (acquirer_id, sale_date)',
  'SELECT 1');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @idx := (SELECT COUNT(1) FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'cs_transaction_acq'
    AND index_name = 'idx_cs_tx_acq_dash_modality');
SET @ddl := IF(@idx = 0,
  'CREATE INDEX idx_cs_tx_acq_dash_modality ON cs_transaction_acq (modality, sale_date)',
  'SELECT 1');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- cs_credit_order: pagamentos por data + dimensões
SET @idx := (SELECT COUNT(1) FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'cs_credit_order'
    AND index_name = 'idx_cs_credit_order_dash_date');
SET @ddl := IF(@idx = 0,
  'CREATE INDEX idx_cs_credit_order_dash_date ON cs_credit_order (release_date, company_id, acquirer_id, flag_id)',
  'SELECT 1');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- cs_adjustment: débitos por motivo + data + dimensões
SET @idx := (SELECT COUNT(1) FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'cs_adjustment'
    AND index_name = 'idx_cs_adjustment_dash');
SET @ddl := IF(@idx = 0,
  'CREATE INDEX idx_cs_adjustment_dash ON cs_adjustment (adjustment_reason, adjustment_date, company_id, acquirer_id, rv_flag_adjustment_id)',
  'SELECT 1');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;