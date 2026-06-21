CREATE TABLE cs_reconciliation_settings (
  id BINARY(16) NOT NULL,
  erp_acquirer_previous_days_lookback INT NOT NULL DEFAULT 0,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NULL,
  created_by_id BINARY(16) NULL,
  updated_by_id BINARY(16) NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_cs_reconciliation_settings_created_by FOREIGN KEY (created_by_id) REFERENCES cs_users (id),
  CONSTRAINT fk_cs_reconciliation_settings_updated_by FOREIGN KEY (updated_by_id) REFERENCES cs_users (id)
);
