ALTER TABLE cs_reconciliation_settings
  ADD COLUMN credit_order_pending_days INT NOT NULL DEFAULT 30;
