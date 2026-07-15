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
 * Postgres já tem ALTER TABLE ... DROP CONSTRAINT IF EXISTS nativo — sem precisar de procedure condicional.
 */

ALTER TABLE cs_contract_audit DROP CONSTRAINT IF EXISTS fk_cs_contract_audit_flag;
ALTER TABLE cs_contract_audit DROP CONSTRAINT IF EXISTS fk_cs_contract_audit_acquirer;
ALTER TABLE cs_contract_audit DROP CONSTRAINT IF EXISTS fk_cs_contract_audit_contract;
ALTER TABLE cs_contract_audit DROP CONSTRAINT IF EXISTS fk_cs_contract_audit_company;
ALTER TABLE cs_contract_audit DROP CONSTRAINT IF EXISTS fk_cs_contract_audit_establishment;
ALTER TABLE cs_contract_audit DROP CONSTRAINT IF EXISTS fk_cs_contract_audit_transaction_acq;
ALTER TABLE cs_contract_audit DROP CONSTRAINT IF EXISTS fk_cs_contract_audit_transaction_erp;
ALTER TABLE cs_contract_audit DROP CONSTRAINT IF EXISTS fk_cs_contract_audit_created_by;
ALTER TABLE cs_contract_audit DROP CONSTRAINT IF EXISTS fk_cs_contract_audit_updated_by;
