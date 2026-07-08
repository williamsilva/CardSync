ALTER TABLE cs_reconciliation_settings
    ADD COLUMN enabled_erp_acquirer               BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN enabled_sales_summary_transactions  BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN enabled_acquirer_sale_cancellations BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN enabled_erp_acquirer_fees           BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN enabled_acquirer_sale_summary       BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN enabled_sales_summary_credit_order  BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN enabled_bank_acquirer               BOOLEAN NOT NULL DEFAULT TRUE;
