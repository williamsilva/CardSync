CREATE TABLE cs_origin_file (
  id UUID NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NULL DEFAULT CURRENT_TIMESTAMP(6),
  created_by_id UUID NULL,
  updated_by_id UUID NULL,
  code VARCHAR(30) NOT NULL,
  name VARCHAR(80) NOT NULL,
  description VARCHAR(150) NULL,
  PRIMARY KEY (id),
  CONSTRAINT uk_cs_origin_file_code UNIQUE (code)
);

CREATE TABLE cs_processed_file (
  id UUID NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NULL DEFAULT CURRENT_TIMESTAMP(6),
  created_by_id UUID NULL,
  updated_by_id UUID NULL,
  file_name VARCHAR(255) NOT NULL,
  file_group VARCHAR(20) NOT NULL,
  status VARCHAR(30) NOT NULL,
  date_file DATE NULL,
  date_import TIMESTAMP(6) NOT NULL,
  date_processing TIMESTAMP(6) NOT NULL,
  started_at TIMESTAMP(6) NULL,
  finished_at TIMESTAMP(6) NULL,
  type_file VARCHAR(120) NULL,
  commercial_name VARCHAR(150) NULL,
  version VARCHAR(80) NULL,
  pv_group_number INT NULL,
  total_lines INT NULL,
  processed_lines INT NULL,
  ignored_lines INT NULL,
  warning_lines INT NULL,
  error_lines INT NULL,
  pending_contract_lines INT NULL,
  pending_business_context_lines INT NULL,
  error_message VARCHAR(500) NULL,
  status_message VARCHAR(500) NULL,
  origin_file_id UUID NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT uk_cs_processed_file_file_origin UNIQUE (file_name, origin_file_id),
  CONSTRAINT fk_cs_processed_file_origin FOREIGN KEY (origin_file_id) REFERENCES cs_origin_file(id) ON UPDATE CASCADE
);

CREATE INDEX idx_cs_processed_file_file_name ON cs_processed_file(file_name);
CREATE INDEX idx_cs_processed_file_group_status ON cs_processed_file(file_group, status);
CREATE INDEX idx_cs_processed_file_date_file ON cs_processed_file(date_file);

CREATE TABLE cs_sales_summary (
  id UUID NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NULL DEFAULT CURRENT_TIMESTAMP(6),
  created_by_id UUID NULL,
  updated_by_id UUID NULL,
  modality INT NULL,
  pv_number INT NULL,
  rv_number INT NULL,
  line_number INT NULL,
  number_cv_nsu INT NULL,
  credit_order_status INT NULL,
  status_payment_bank INT NULL,
  transactions_status INT NULL,
  record_type VARCHAR(20) NULL,
  tip_value DECIMAL(18,8) NULL,
  gross_value DECIMAL(18,8) NULL,
  liquid_value DECIMAL(18,8) NULL,
  adjusted_value DECIMAL(18,8) NULL,
  discount_value DECIMAL(18,8) NULL,
  rejected_value DECIMAL(18,8) NULL,
  manual_generated BOOLEAN NULL,
  rv_date DATE NULL,
  first_installment_credit_date DATE NULL,
  flag_id UUID NULL,
  acquirer_id UUID NULL,
  company_id UUID NULL,
  processed_file_id UUID NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_cs_sales_summary_flag FOREIGN KEY (flag_id) REFERENCES cs_flag(id) ON UPDATE CASCADE,
  CONSTRAINT fk_cs_sales_summary_acquirer FOREIGN KEY (acquirer_id) REFERENCES cs_acquirer(id) ON UPDATE CASCADE,
  CONSTRAINT fk_cs_sales_summary_company FOREIGN KEY (company_id) REFERENCES cs_company(id) ON UPDATE CASCADE,
  CONSTRAINT fk_cs_sales_summary_processed_file FOREIGN KEY (processed_file_id) REFERENCES cs_processed_file(id) ON UPDATE CASCADE
);

CREATE INDEX idx_cs_sales_summary_rv_number ON cs_sales_summary(rv_number);
CREATE INDEX idx_cs_sales_summary_pv_number ON cs_sales_summary(pv_number);
CREATE INDEX idx_cs_sales_summary_rv_date ON cs_sales_summary(rv_date);

CREATE TABLE cs_transaction_acq (
  id UUID NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NULL DEFAULT CURRENT_TIMESTAMP(6),
  created_by_id UUID NULL,
  updated_by_id UUID NULL,
  nsu BIGINT NULL,
  canceled_date DATE NULL,
  sale_date TIMESTAMP(6) NULL,
  sale_reconciliation_date TIMESTAMP(6) NULL,
  tid VARCHAR(80) NULL,
  machine VARCHAR(80) NULL,
  status_cv VARCHAR(80) NULL,
  record_type VARCHAR(20) NULL,
  card_number VARCHAR(80) NULL,
  "authorization" VARCHAR(80) NULL,
  reference_number VARCHAR(80) NULL,
  capture INT NULL,
  modality INT NULL,
  rv_number INT NULL,
  line_number INT NULL,
  status_audit INT NULL,
  installment INT NULL,
  status_transaction INT NULL,
  status_payment_bank INT NULL,
  status_transaction_reason INT NULL,
  mdr_rate DECIMAL(18,8) NULL,
  flex_rate DECIMAL(18,8) NULL,
  tip_value DECIMAL(18,8) NULL,
  gross_value DECIMAL(18,8) NULL,
  liquid_value DECIMAL(18,8) NULL,
  discount_value DECIMAL(18,8) NULL,
  first_installment_value DECIMAL(18,8) NULL,
  other_installments_value DECIMAL(18,8) NULL,
  flag_id UUID NULL,
  adjustment_id UUID NULL,
  acquirer_id UUID NULL,
  company_id UUID NULL,
  processed_file_id UUID NULL,
  sales_summary_id UUID NULL,
  establishment_id UUID NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_cs_transaction_acq_flag FOREIGN KEY (flag_id) REFERENCES cs_flag(id) ON UPDATE CASCADE,
  CONSTRAINT fk_cs_transaction_acq_acquirer FOREIGN KEY (acquirer_id) REFERENCES cs_acquirer(id) ON UPDATE CASCADE,
  CONSTRAINT fk_cs_transaction_acq_company FOREIGN KEY (company_id) REFERENCES cs_company(id) ON UPDATE CASCADE,
  CONSTRAINT fk_cs_transaction_acq_processed_file FOREIGN KEY (processed_file_id) REFERENCES cs_processed_file(id) ON UPDATE CASCADE,
  CONSTRAINT fk_cs_transaction_acq_sales_summary FOREIGN KEY (sales_summary_id) REFERENCES cs_sales_summary(id) ON UPDATE CASCADE,
  CONSTRAINT fk_cs_transaction_acq_establishment FOREIGN KEY (establishment_id) REFERENCES cs_establishment(id) ON UPDATE CASCADE
);

CREATE INDEX idx_cs_transaction_acq_nsu ON cs_transaction_acq(nsu);
CREATE INDEX idx_cs_transaction_acq_rv_number ON cs_transaction_acq(rv_number);
CREATE INDEX idx_cs_transaction_acq_sale_date ON cs_transaction_acq(sale_date);
CREATE INDEX idx_cs_transaction_acq_processed_file ON cs_transaction_acq(processed_file_id);

INSERT INTO cs_origin_file (id, created_at, code, name, description) VALUES
  (gen_random_uuid(), CURRENT_TIMESTAMP(6), 'ERP', 'ERP', 'Arquivos CSV do ERP'),
  (gen_random_uuid(), CURRENT_TIMESTAMP(6), 'REDE', 'Rede', 'Arquivos da adquirente Rede')
ON CONFLICT (code) DO UPDATE SET
  name = EXCLUDED.name,
  description = EXCLUDED.description;

ALTER TABLE cs_sales_summary
  ADD COLUMN bank INT NULL,
  ADD COLUMN agency INT NULL,
  ADD COLUMN current_account INT NULL,
  ADD COLUMN summary_type VARCHAR(10) NULL,
  ADD COLUMN banking_domicile_id UUID NULL;

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
