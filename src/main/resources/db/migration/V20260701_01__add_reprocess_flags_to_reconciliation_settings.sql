-- Adiciona flags de reprocessamento por etapa da esteira de conciliação.
-- Cada flag controla se a etapa correspondente deve reprocessar registros já conciliados.
-- Todas com DEFAULT FALSE para não alterar comportamento em instalações existentes.

ALTER TABLE cs_reconciliation_settings
    -- Etapa 1: ERP x Adquirente (vendas)
    ADD COLUMN reprocess_erp_acquirer_sales          TINYINT(1) NOT NULL DEFAULT 0,
    -- Etapa 2: Resumo de vendas x TransactionAcq
    ADD COLUMN reprocess_sales_summary_transactions  TINYINT(1) NOT NULL DEFAULT 0,
    -- Etapa 3: Cancelamentos da adquirente
    ADD COLUMN reprocess_acquirer_sale_cancellations TINYINT(1) NOT NULL DEFAULT 0,
    -- Etapa 4: Taxas ERP x Adquirente
    ADD COLUMN reprocess_erp_acquirer_fees           TINYINT(1) NOT NULL DEFAULT 0,
    -- Etapa 5: Venda ADQ x Resumo de vendas
    ADD COLUMN reprocess_acquirer_sale_summary       TINYINT(1) NOT NULL DEFAULT 0,
    -- Etapa 6: Resumo de vendas x Ordem de pagamento
    ADD COLUMN reprocess_sales_summary_credit_order  TINYINT(1) NOT NULL DEFAULT 0,
    -- Etapa 7: Ordem de pagamento x Lançamento bancário
    ADD COLUMN reprocess_bank_acquirer               TINYINT(1) NOT NULL DEFAULT 0;
