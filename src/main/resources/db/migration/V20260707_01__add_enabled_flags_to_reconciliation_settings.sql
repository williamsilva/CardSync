ALTER TABLE cs_reconciliation_settings
    ADD COLUMN enabled_erp_acquirer               BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN enabled_sales_summary_transactions  BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN enabled_acquirer_sale_cancellations BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN enabled_erp_acquirer_fees           BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN enabled_acquirer_sale_summary       BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN enabled_sales_summary_credit_order  BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN enabled_bank_acquirer               BOOLEAN NOT NULL DEFAULT TRUE;

INSERT INTO cs_reconciliation_settings
	(id, created_at, erp_acquirer_previous_days_lookback, erp_acquirer_future_days_lookback, reconciliation_lookback_months, credit_order_pending_days)
	VALUES (UUID_TO_BIN(UUID()), NOW(), '30', '30', '120', '30');