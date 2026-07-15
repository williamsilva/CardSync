-- Adiciona flags de reprocessamento por etapa da esteira de conciliação.
-- Cada flag controla se a etapa correspondente deve reprocessar registros já conciliados.
-- Todas com DEFAULT FALSE para não alterar comportamento em instalações existentes.

ALTER TABLE cs_reconciliation_settings
    -- Etapa 1: ERP x Adquirente (vendas)
    ADD COLUMN reprocess_erp_acquirer_sales          BOOLEAN NOT NULL DEFAULT FALSE,
    -- Etapa 2: Resumo de vendas x TransactionAcq
    ADD COLUMN reprocess_sales_summary_transactions  BOOLEAN NOT NULL DEFAULT FALSE,
    -- Etapa 3: Cancelamentos da adquirente
    ADD COLUMN reprocess_acquirer_sale_cancellations BOOLEAN NOT NULL DEFAULT FALSE,
    -- Etapa 4: Taxas ERP x Adquirente
    ADD COLUMN reprocess_erp_acquirer_fees           BOOLEAN NOT NULL DEFAULT FALSE,
    -- Etapa 5: Venda ADQ x Resumo de vendas
    ADD COLUMN reprocess_acquirer_sale_summary       BOOLEAN NOT NULL DEFAULT FALSE,
    -- Etapa 6: Resumo de vendas x Ordem de pagamento
    ADD COLUMN reprocess_sales_summary_credit_order  BOOLEAN NOT NULL DEFAULT FALSE,
    -- Etapa 7: Ordem de pagamento x Lançamento bancário
    ADD COLUMN reprocess_bank_acquirer               BOOLEAN NOT NULL DEFAULT FALSE;
