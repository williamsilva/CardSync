ALTER TABLE cs_reconciliation_settings
  ADD COLUMN date_tolerance_days                INT            NOT NULL DEFAULT 10,
  ADD COLUMN value_tolerance                    DECIMAL(10, 4) NOT NULL DEFAULT 0.0500,
  ADD COLUMN bank_mark_not_reconciled_after_days INT            NOT NULL DEFAULT 3;
