-- =====================================================================
-- Índices de cobertura para pesquisas das telas de lista
-- MySQL 8 / InnoDB
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
--   3. Índice funcional em LOWER(authorization) — a busca usa
--      LOWER(authorization) LIKE 'valor%', o índice normal não ajuda.
--   4. Índice em status_transaction_reason — usado nas telas de
--      "aguardando conciliação" (CV não encontrado ERP/ADQ).
--   5. Índice de cobertura para count — quando não há filtros
--      ativos, o count deve ser resolvido por índice, não full scan.
--
-- Safe: todos os índices verificam existência antes de criar.
-- =====================================================================

DROP PROCEDURE IF EXISTS cs_create_index_if_missing;

DELIMITER $$

CREATE PROCEDURE cs_create_index_if_missing(
    IN p_table_name VARCHAR(128),
    IN p_index_name VARCHAR(128),
    IN p_index_columns TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
          FROM information_schema.statistics
         WHERE table_schema = DATABASE()
           AND table_name   = p_table_name
           AND index_name   = p_index_name
    ) THEN
        SET @sql = CONCAT(
            'CREATE INDEX `',
            REPLACE(p_index_name, '`', '``'),
            '` ON `',
            REPLACE(p_table_name, '`', '``'),
            '` (', p_index_columns, ')'
        );
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

DELIMITER ;

-- ─────────────────────────────────────────────────────────────────────
-- cs_transaction_acq
-- ─────────────────────────────────────────────────────────────────────

-- Filtro por status de conciliação + data de venda (tela principal de ADQ).
-- WHERE modality <> 8 AND status_transaction = ? ORDER BY sale_date DESC
-- Cobre a exclusão de DIGITAL_WALLET (modality) que é aplicada em TODA query.
CALL cs_create_index_if_missing(
    'cs_transaction_acq',
    'idx_acq_modality_status_sale_date',
    '`modality`, `status_transaction`, `sale_date` DESC, `id` DESC'
);

-- Filtro por status_transaction_reason (tela "aguardando conciliação ADQ").
-- WHERE status_transaction_reason = ? AND modality <> 8
CALL cs_create_index_if_missing(
    'cs_transaction_acq',
    'idx_acq_reason_modality_sale_date',
    '`status_transaction_reason`, `modality`, `sale_date` DESC, `id` DESC'
);

-- Filtro por data de venda isolado (seletor de período sem outros filtros).
-- ORDER BY sale_date DESC já coberto por idx_acq_sale_date_id.
-- Adicionar modality para filtro universal:
CALL cs_create_index_if_missing(
    'cs_transaction_acq',
    'idx_acq_sale_date_modality',
    '`sale_date` DESC, `modality`, `id` DESC'
);

-- Índice funcional LOWER(authorization) para busca por prefixo.
-- startsWith("authorization") gera: WHERE LOWER(authorization) LIKE 'valor%'
-- Requer MySQL 8.0.13+ (expressão em índice).
CALL cs_create_index_if_missing(
    'cs_transaction_acq',
    'idx_acq_authorization_lower',
    '(LOWER(`authorization`))'
);

-- Count rápido sem filtros ativos: modality <> 8 é o único predicado constante.
-- InnoDB conta pelo índice secundário mais estreito disponível.
CALL cs_create_index_if_missing(
    'cs_transaction_acq',
    'idx_acq_modality_id',
    '`modality`, `id`'
);

-- ─────────────────────────────────────────────────────────────────────
-- cs_transaction_erp
-- ─────────────────────────────────────────────────────────────────────

-- Mesmo padrão: exclusão de DIGITAL_WALLET + status + data.
CALL cs_create_index_if_missing(
    'cs_transaction_erp',
    'idx_erp_modality_status_sale_date',
    '`modality`, `status_transaction`, `sale_date` DESC, `id` DESC'
);

-- Filtro por status_transaction_reason (tela "aguardando conciliação ERP").
CALL cs_create_index_if_missing(
    'cs_transaction_erp',
    'idx_erp_reason_modality_sale_date',
    '`status_transaction_reason`, `modality`, `sale_date` DESC, `id` DESC'
);

-- Filtro por data com exclusão de modality.
CALL cs_create_index_if_missing(
    'cs_transaction_erp',
    'idx_erp_sale_date_modality',
    '`sale_date` DESC, `modality`, `id` DESC'
);

-- Índice funcional LOWER(authorization).
CALL cs_create_index_if_missing(
    'cs_transaction_erp',
    'idx_erp_authorization_lower',
    '(LOWER(`authorization`))'
);

-- Count rápido.
CALL cs_create_index_if_missing(
    'cs_transaction_erp',
    'idx_erp_modality_id',
    '`modality`, `id`'
);

-- ─────────────────────────────────────────────────────────────────────
-- cs_installment_acq
-- ─────────────────────────────────────────────────────────────────────

-- Filtro por transaction_id + data de pagamento esperado (sort padrão).
-- Já existe idx_inst_acq_transaction_expected_date — cobre ORDER BY.
-- Adicionar status_payment_bank para filtro por status na tela de parcelas:
CALL cs_create_index_if_missing(
    'cs_installment_acq',
    'idx_inst_acq_status_transaction_expected',
    '`status_payment_bank`, `transaction_id`, `expected_payment_date` DESC, `id` DESC'
);

-- Filtro por data de pagamento (payment_date) — tela de parcelas pagas.
-- ORDER BY expected_payment_date DESC já coberto por idx_inst_acq_expected_payment_date.
CALL cs_create_index_if_missing(
    'cs_installment_acq',
    'idx_inst_acq_payment_date_status',
    '`payment_date` DESC, `status_payment_bank`, `id` DESC'
);

-- ─────────────────────────────────────────────────────────────────────
-- cs_installment_erp
-- ─────────────────────────────────────────────────────────────────────

CALL cs_create_index_if_missing(
    'cs_installment_erp',
    'idx_inst_erp_status_transaction_expected',
    '`status_payment_bank`, `transaction_id`, `expected_payment_date` DESC, `id` DESC'
);

-- ─────────────────────────────────────────────────────────────────────
-- cs_sales_summary
-- ─────────────────────────────────────────────────────────────────────

-- Tela de resumos: filtro por status de transações + data RV (sort padrão pv_number).
-- transactions_status usado na esteira de conciliação e na tela de resumos.
CALL cs_create_index_if_missing(
    'cs_sales_summary',
    'idx_sales_summary_transactions_status_rv_date',
    '`transactions_status`, `rv_date` DESC, `id` DESC'
);

-- Cobertura para count rápido de resumos (sem filtros ativos).
CALL cs_create_index_if_missing(
    'cs_sales_summary',
    'idx_sales_summary_rv_date_id',
    '`rv_date` DESC, `id` DESC'
);

-- ─────────────────────────────────────────────────────────────────────
-- cs_credit_order
-- ─────────────────────────────────────────────────────────────────────

-- Tela de ordens de crédito: filtro por status de conciliação + data (sort rv_date).
-- reconciliation_status já coberto por idx_credit_order_status_release_date,
-- mas sem banking_domicile_id que é filtro comum na tela.
CALL cs_create_index_if_missing(
    'cs_credit_order',
    'idx_credit_order_banking_domicile_release_date',
    '`banking_domicile_id`, `release_date` DESC, `id` DESC'
);

DROP PROCEDURE IF EXISTS cs_create_index_if_missing;

-- ─────────────────────────────────────────────────────────────────────
-- NOTA: Após aplicar esta migration, execute no banco de produção:
--
--   ANALYZE TABLE cs_transaction_acq;
--   ANALYZE TABLE cs_transaction_erp;
--   ANALYZE TABLE cs_installment_acq;
--   ANALYZE TABLE cs_installment_erp;
--   ANALYZE TABLE cs_sales_summary;
--   ANALYZE TABLE cs_credit_order;
--
-- Isso força o MySQL a atualizar as estatísticas do otimizador e
-- garantir que os novos índices sejam considerados nos query plans.
-- ─────────────────────────────────────────────────────────────────────