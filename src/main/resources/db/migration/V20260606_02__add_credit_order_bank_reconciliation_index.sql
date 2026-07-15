-- =====================================================================
-- Índice de performance para a conciliação Banco x Ordem de Crédito.
-- Postgres
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
-- Postgres já tem CREATE INDEX IF NOT EXISTS nativo — sem precisar de
-- variável de sessão/DDL dinâmico.
-- =====================================================================

CREATE INDEX IF NOT EXISTS idx_cs_credit_order_bank_recon
  ON cs_credit_order (
    company_id,
    banking_domicile_id,
    reconciliation_status,
    sales_summary_status,
    release_date,
    release_value
  );
