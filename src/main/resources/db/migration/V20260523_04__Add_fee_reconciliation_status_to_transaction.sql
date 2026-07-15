ALTER TABLE cs_transaction_erp
  ADD COLUMN fee_reconciliation_status INT NOT NULL DEFAULT 1;

CREATE INDEX idx_cs_transaction_erp_fee_reconciliation_status
  ON cs_transaction_erp (fee_reconciliation_status, status_transaction, transaction_acq_id, sale_date, id);


ALTER TABLE cs_transaction_acq
  ADD COLUMN fee_reconciliation_status INT NOT NULL DEFAULT 1;

CREATE INDEX idx_cs_transaction_acq_fee_reconciliation_status
  ON cs_transaction_acq (fee_reconciliation_status, status_transaction, sale_date, id);
