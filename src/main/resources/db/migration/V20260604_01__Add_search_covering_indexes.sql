-- =====================================================================
-- Índices de cobertura para pesquisas das telas de lista
-- Postgres
--
-- Contexto: tabelas com 200k+ linhas. O objetivo é garantir que as
-- queries de pesquisa mais comuns encontrem um índice utilizável e
-- não façam full scan.
--
-- Estratégia:
--   1. Índices de cobertura (covering index) para filtros frequentes
--      combinados com a ordenação padrão (sale_date DESC, id DESC).
--   2. Índices para filtros de status + data — evitam full scan nas
--      telas de conciliação pendente.
--   3. Índice funcional em LOWER("authorization") — a busca usa
--      LOWER("authorization") LIKE 'valor%', o índice normal não ajuda.
--   4. Índice em status_transaction_reason — usado nas telas de
--      "aguardando conciliação" (CV não encontrado ERP/ADQ).
--   5. Índice de cobertura para count — quando não há filtros
--      ativos, o count deve ser resolvido por índice, não full scan.
--
-- Postgres já tem CREATE INDEX IF NOT EXISTS nativo — sem precisar de
-- procedure condicional.
-- =====================================================================

-- ─────────────────────────────────────────────────────────────────────
-- cs_transaction_acq
-- ─────────────────────────────────────────────────────────────────────

-- Filtro por status de conciliação + data de venda (tela principal de ADQ).
-- WHERE modality <> 8 AND status_transaction = ? ORDER BY sale_date DESC
-- Cobre a exclusão de DIGITAL_WALLET (modality) que é aplicada em TODA query.
CREATE INDEX IF NOT EXISTS idx_acq_modality_status_sale_date
    ON cs_transaction_acq (modality, status_transaction, sale_date DESC, id DESC);

-- Filtro por status_transaction_reason (tela "aguardando conciliação ADQ").
-- WHERE status_transaction_reason = ? AND modality <> 8
CREATE INDEX IF NOT EXISTS idx_acq_reason_modality_sale_date
    ON cs_transaction_acq (status_transaction_reason, modality, sale_date DESC, id DESC);

-- Filtro por data de venda isolado (seletor de período sem outros filtros).
-- ORDER BY sale_date DESC já coberto por idx_acq_sale_date_id.
-- Adicionar modality para filtro universal:
CREATE INDEX IF NOT EXISTS idx_acq_sale_date_modality
    ON cs_transaction_acq (sale_date DESC, modality, id DESC);

-- Índice funcional LOWER("authorization") para busca por prefixo.
-- startsWith("authorization") gera: WHERE LOWER("authorization") LIKE 'valor%'
-- text_pattern_ops é obrigatório aqui: o banco usa collation pt-BR (não "C"), e sem essa
-- opclass um LIKE 'valor%' não consegue usar o índice B-tree (só teria efeito com
-- collation "C"/POSIX por padrão) — ver nota de locale na migração.
CREATE INDEX IF NOT EXISTS idx_acq_authorization_lower
    ON cs_transaction_acq (LOWER("authorization") text_pattern_ops);

-- Count rápido sem filtros ativos: modality <> 8 é o único predicado constante.
CREATE INDEX IF NOT EXISTS idx_acq_modality_id
    ON cs_transaction_acq (modality, id);

-- ─────────────────────────────────────────────────────────────────────
-- cs_transaction_erp
-- ─────────────────────────────────────────────────────────────────────

-- Mesmo padrão: exclusão de DIGITAL_WALLET + status + data.
CREATE INDEX IF NOT EXISTS idx_erp_modality_status_sale_date
    ON cs_transaction_erp (modality, status_transaction, sale_date DESC, id DESC);

-- Filtro por status_transaction_reason (tela "aguardando conciliação ERP").
CREATE INDEX IF NOT EXISTS idx_erp_reason_modality_sale_date
    ON cs_transaction_erp (status_transaction_reason, modality, sale_date DESC, id DESC);

-- Filtro por data com exclusão de modality.
CREATE INDEX IF NOT EXISTS idx_erp_sale_date_modality
    ON cs_transaction_erp (sale_date DESC, modality, id DESC);

-- Índice funcional LOWER("authorization"). text_pattern_ops pelo mesmo motivo do índice
-- equivalente em cs_transaction_acq acima (collation pt-BR não é "C"/POSIX).
CREATE INDEX IF NOT EXISTS idx_erp_authorization_lower
    ON cs_transaction_erp (LOWER("authorization") text_pattern_ops);

-- Count rápido.
CREATE INDEX IF NOT EXISTS idx_erp_modality_id
    ON cs_transaction_erp (modality, id);

-- ─────────────────────────────────────────────────────────────────────
-- cs_installment_acq
-- ─────────────────────────────────────────────────────────────────────

-- Filtro por transaction_id + data de pagamento esperado (sort padrão).
-- Já existe idx_inst_acq_transaction_expected_date — cobre ORDER BY.
-- Adicionar status_payment_bank para filtro por status na tela de parcelas:
CREATE INDEX IF NOT EXISTS idx_inst_acq_status_transaction_expected
    ON cs_installment_acq (status_payment_bank, transaction_id, expected_payment_date DESC, id DESC);

-- Filtro por data de pagamento (payment_date) — tela de parcelas pagas.
-- ORDER BY expected_payment_date DESC já coberto por idx_inst_acq_expected_payment_date.
CREATE INDEX IF NOT EXISTS idx_inst_acq_payment_date_status
    ON cs_installment_acq (payment_date DESC, status_payment_bank, id DESC);

-- ─────────────────────────────────────────────────────────────────────
-- cs_installment_erp
-- ─────────────────────────────────────────────────────────────────────

CREATE INDEX IF NOT EXISTS idx_inst_erp_status_transaction_expected
    ON cs_installment_erp (status_payment_bank, transaction_id, expected_payment_date DESC, id DESC);

-- ─────────────────────────────────────────────────────────────────────
-- cs_sales_summary
-- ─────────────────────────────────────────────────────────────────────

-- Tela de resumos: filtro por status de transações + data RV (sort padrão pv_number).
-- transactions_status usado na esteira de conciliação e na tela de resumos.
CREATE INDEX IF NOT EXISTS idx_sales_summary_transactions_status_rv_date
    ON cs_sales_summary (transactions_status, rv_date DESC, id DESC);

-- Cobertura para count rápido de resumos (sem filtros ativos).
CREATE INDEX IF NOT EXISTS idx_sales_summary_rv_date_id
    ON cs_sales_summary (rv_date DESC, id DESC);

-- ─────────────────────────────────────────────────────────────────────
-- cs_credit_order
-- ─────────────────────────────────────────────────────────────────────

-- Tela de ordens de crédito: filtro por status de conciliação + data (sort rv_date).
-- reconciliation_status já coberto por idx_credit_order_status_release_date,
-- mas sem banking_domicile_id que é filtro comum na tela.
CREATE INDEX IF NOT EXISTS idx_credit_order_banking_domicile_release_date
    ON cs_credit_order (banking_domicile_id, release_date DESC, id DESC);

-- ─────────────────────────────────────────────────────────────────────
-- NOTA: Após aplicar esta migration, execute no banco de produção:
--
--   ANALYZE cs_transaction_acq;
--   ANALYZE cs_transaction_erp;
--   ANALYZE cs_installment_acq;
--   ANALYZE cs_installment_erp;
--   ANALYZE cs_sales_summary;
--   ANALYZE cs_credit_order;
--
-- Isso força o Postgres a atualizar as estatísticas do otimizador e
-- garantir que os novos índices sejam considerados nos query plans.
-- ─────────────────────────────────────────────────────────────────────
