/*
 * Evita Lock wait timeout durante a conciliação ERP x adquirente.
 *
 * cs_contract_audit é uma tabela de auditoria/histórico. Ela guarda os UUIDs das entidades
 * relacionadas, mas não deve bloquear o fluxo transacional das tabelas quentes
 * cs_transaction_acq/cs_transaction_erp durante a conciliação em lote.
 *
 * Em MySQL/InnoDB, INSERT em uma tabela filha com FK pode aguardar locks das linhas pai.
 * Como a conciliação atualiza transação ERP/adquirente/parcelas e depois grava auditoria,
 * as FKs desta tabela podem causar "Lock wait timeout exceeded".
 *
 * Mantemos os índices para consulta, mas removemos as constraints de FK da tabela de auditoria.
 */

DROP PROCEDURE IF EXISTS cs_drop_fk_if_exists;

DELIMITER $$

CREATE PROCEDURE cs_drop_fk_if_exists(
  IN p_table_name VARCHAR(128),
  IN p_constraint_name VARCHAR(128)
)
BEGIN
  IF EXISTS (
    SELECT 1
    FROM information_schema.table_constraints tc
    WHERE tc.constraint_schema = DATABASE()
      AND tc.table_name = p_table_name
      AND tc.constraint_name = p_constraint_name
      AND tc.constraint_type = 'FOREIGN KEY'
  ) THEN
    SET @sql = CONCAT('ALTER TABLE `', p_table_name, '` DROP FOREIGN KEY `', p_constraint_name, '`');
    PREPARE stmt FROM @sql;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END IF;
END$$

DELIMITER ;

CALL cs_drop_fk_if_exists('cs_contract_audit', 'fk_cs_contract_audit_flag');
CALL cs_drop_fk_if_exists('cs_contract_audit', 'fk_cs_contract_audit_acquirer');
CALL cs_drop_fk_if_exists('cs_contract_audit', 'fk_cs_contract_audit_contract');
CALL cs_drop_fk_if_exists('cs_contract_audit', 'fk_cs_contract_audit_company');
CALL cs_drop_fk_if_exists('cs_contract_audit', 'fk_cs_contract_audit_establishment');
CALL cs_drop_fk_if_exists('cs_contract_audit', 'fk_cs_contract_audit_transaction_acq');
CALL cs_drop_fk_if_exists('cs_contract_audit', 'fk_cs_contract_audit_transaction_erp');
CALL cs_drop_fk_if_exists('cs_contract_audit', 'fk_cs_contract_audit_created_by');
CALL cs_drop_fk_if_exists('cs_contract_audit', 'fk_cs_contract_audit_updated_by');

DROP PROCEDURE IF EXISTS cs_drop_fk_if_exists;
