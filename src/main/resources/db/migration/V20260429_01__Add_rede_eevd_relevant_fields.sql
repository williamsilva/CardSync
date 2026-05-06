ALTER TABLE cs_sales_summary
  ADD COLUMN bank INT NULL,
  ADD COLUMN agency INT NULL,
  ADD COLUMN current_account INT NULL,
  ADD COLUMN summary_type VARCHAR(10) NULL,
  ADD COLUMN banking_domicile_id BINARY(16) NULL;

ALTER TABLE cs_sales_summary
  ADD CONSTRAINT fk_cs_sales_summary_banking_domicile FOREIGN KEY (banking_domicile_id) REFERENCES cs_banking_domicile(id) ON UPDATE CASCADE;

CREATE INDEX idx_cs_sales_summary_banking_domicile ON cs_sales_summary(banking_domicile_id);
CREATE INDEX idx_cs_sales_summary_bank_account ON cs_sales_summary(bank, agency, current_account);

ALTER TABLE cs_transaction_acq
  ADD COLUMN credit_date DATE NULL,
  ADD COLUMN transaction_type VARCHAR(10) NULL,
  ADD COLUMN dcc_currency VARCHAR(10) NULL,
  ADD COLUMN service_code VARCHAR(10) NULL,
  ADD COLUMN purchase_value DECIMAL(18,8) NULL,
  ADD COLUMN withdrawal_value DECIMAL(18,8) NULL;

CREATE INDEX idx_cs_transaction_acq_credit_date ON cs_transaction_acq(credit_date);
CREATE INDEX idx_cs_transaction_acq_type_service ON cs_transaction_acq(transaction_type, service_code);

ALTER TABLE cs_adjustment
  ADD COLUMN ecommerce_order_number VARCHAR(80) NULL,
  ADD COLUMN letter_reference VARCHAR(80) NULL;

CREATE INDEX idx_cs_adjustment_ecommerce_order ON cs_adjustment(ecommerce_order_number);
CREATE INDEX idx_cs_adjustment_letter_reference ON cs_adjustment(letter_reference);
