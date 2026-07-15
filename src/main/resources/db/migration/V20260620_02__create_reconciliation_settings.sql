CREATE TABLE cs_reconciliation_settings (
  id UUID NOT NULL,
  erp_acquirer_previous_days_lookback INT NOT NULL DEFAULT 0,
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NULL,
  created_by_id UUID NULL,
  updated_by_id UUID NULL,
  PRIMARY KEY (id)
);
