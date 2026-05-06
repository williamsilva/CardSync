ALTER TABLE cs_transaction_erp
  ADD COLUMN commercial_status VARCHAR(40) NULL AFTER contracted_fee,
  ADD COLUMN commercial_status_message VARCHAR(500) NULL AFTER commercial_status;

CREATE INDEX idx_cs_transaction_erp_commercial_status ON cs_transaction_erp(commercial_status);

ALTER TABLE cs_processed_file
  ADD COLUMN pending_contract_lines INT NULL AFTER error_lines,
  ADD COLUMN pending_business_context_lines INT NULL AFTER pending_contract_lines;
