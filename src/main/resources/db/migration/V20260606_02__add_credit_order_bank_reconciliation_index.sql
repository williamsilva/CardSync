-- =====================================================================
-- Índice de performance para a conciliação Banco x Ordem de Crédito.
-- MySQL 8 / InnoDB
--
-- Contexto: a conciliação bancária itera centenas de releases e, para cada
-- release, busca ordens de crédito candidatas filtrando por:
--   release_bank IS NULL, reconciliation_status, sales_summary_status,
--   company_id, banking_domicile_id, (acquirer_id), (flag_id), release_date
-- e ordena por release_date, release_value.
--
-- Sem um índice composto, cada release faz full scan + filesort em
-- cs_credit_order, o que explicava ~3s por release (esteira ~1h).
--
-- Idempotente: cria o índice apenas se ainda não existir, sem depender de
-- DELIMITER (que não é interpretado pelo Flyway/JDBC).
-- =====================================================================

SET @idx_exists := (
  SELECT COUNT(1)
    FROM information_schema.statistics
   WHERE table_schema = DATABASE()
     AND table_name   = 'cs_credit_order'
     AND index_name   = 'idx_cs_credit_order_bank_recon'
);

SET @ddl := IF(
  @idx_exists = 0,
  'CREATE INDEX idx_cs_credit_order_bank_recon
     ON cs_credit_order (
       company_id,
       banking_domicile_id,
       reconciliation_status,
       sales_summary_status,
       release_date,
       release_value
     )',
  'SELECT 1'
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;