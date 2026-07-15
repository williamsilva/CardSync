ALTER TABLE cs_reconciliation_settings
  ADD COLUMN erp_acquirer_future_days_lookback INT NOT NULL DEFAULT 0;
