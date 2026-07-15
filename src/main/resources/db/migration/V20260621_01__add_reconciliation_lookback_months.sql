ALTER TABLE cs_reconciliation_settings
  ADD COLUMN reconciliation_lookback_months INT NOT NULL DEFAULT 6;
