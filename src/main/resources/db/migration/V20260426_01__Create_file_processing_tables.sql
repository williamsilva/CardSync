CREATE TABLE cs_origin_file (
  id BINARY(16) NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  created_by_id BINARY(16) NULL,
  updated_by_id BINARY(16) NULL,
  code VARCHAR(30) NOT NULL,
  name VARCHAR(80) NOT NULL,
  description VARCHAR(150) NULL,
  PRIMARY KEY (id),
  CONSTRAINT uk_cs_origin_file_code UNIQUE (code),
  CONSTRAINT fk_cs_origin_file_created_by FOREIGN KEY (created_by_id) REFERENCES cs_users(id) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT fk_cs_origin_file_updated_by FOREIGN KEY (updated_by_id) REFERENCES cs_users(id) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB;

CREATE TABLE cs_processed_file (
  id BINARY(16) NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  created_by_id BINARY(16) NULL,
  updated_by_id BINARY(16) NULL,
  file_name VARCHAR(255) NOT NULL,
  file_group VARCHAR(20) NOT NULL,
  status VARCHAR(30) NOT NULL,
  date_file DATE NULL,
  date_import DATETIME(6) NOT NULL,
  date_processing DATETIME(6) NOT NULL,
  type_file VARCHAR(120) NULL,
  commercial_name VARCHAR(150) NULL,
  version VARCHAR(80) NULL,
  pv_group_number INT NULL,
  total_lines INT NULL,
  processed_lines INT NULL,
  ignored_lines INT NULL,
  error_message VARCHAR(500) NULL,
  origin_file_id BINARY(16) NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT uk_cs_processed_file_file_origin UNIQUE (file_name, origin_file_id),
  CONSTRAINT fk_cs_processed_file_origin FOREIGN KEY (origin_file_id) REFERENCES cs_origin_file(id) ON UPDATE CASCADE,
  CONSTRAINT fk_cs_processed_file_created_by FOREIGN KEY (created_by_id) REFERENCES cs_users(id) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT fk_cs_processed_file_updated_by FOREIGN KEY (updated_by_id) REFERENCES cs_users(id) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB;

CREATE INDEX idx_cs_processed_file_file_name ON cs_processed_file(file_name);
CREATE INDEX idx_cs_processed_file_group_status ON cs_processed_file(file_group, status);
CREATE INDEX idx_cs_processed_file_date_file ON cs_processed_file(date_file);

CREATE TABLE cs_sales_summary (
  id BINARY(16) NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  created_by_id BINARY(16) NULL,
  updated_by_id BINARY(16) NULL,
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
  manual_generated BIT NULL,
  rv_date DATE NULL,
  first_installment_credit_date DATE NULL,
  flag_id BINARY(16) NULL,
  acquirer_id BINARY(16) NULL,
  company_id BINARY(16) NULL,
  processed_file_id BINARY(16) NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_cs_sales_summary_flag FOREIGN KEY (flag_id) REFERENCES cs_flag(id) ON UPDATE CASCADE,
  CONSTRAINT fk_cs_sales_summary_acquirer FOREIGN KEY (acquirer_id) REFERENCES cs_acquirer(id) ON UPDATE CASCADE,
  CONSTRAINT fk_cs_sales_summary_company FOREIGN KEY (company_id) REFERENCES cs_company(id) ON UPDATE CASCADE,
  CONSTRAINT fk_cs_sales_summary_processed_file FOREIGN KEY (processed_file_id) REFERENCES cs_processed_file(id) ON UPDATE CASCADE,
  CONSTRAINT fk_cs_sales_summary_created_by FOREIGN KEY (created_by_id) REFERENCES cs_users(id) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT fk_cs_sales_summary_updated_by FOREIGN KEY (updated_by_id) REFERENCES cs_users(id) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB;

CREATE INDEX idx_cs_sales_summary_rv_number ON cs_sales_summary(rv_number);
CREATE INDEX idx_cs_sales_summary_pv_number ON cs_sales_summary(pv_number);
CREATE INDEX idx_cs_sales_summary_rv_date ON cs_sales_summary(rv_date);

CREATE TABLE cs_transaction_acq (
  id BINARY(16) NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  created_by_id BINARY(16) NULL,
  updated_by_id BINARY(16) NULL,
  nsu BIGINT NULL,
  canceled_date DATE NULL,
  sale_date DATETIME(6) NULL,
  sale_reconciliation_date DATETIME(6) NULL,
  tid VARCHAR(80) NULL,
  machine VARCHAR(80) NULL,
  status_cv VARCHAR(80) NULL,
  record_type VARCHAR(20) NULL,
  card_number VARCHAR(80) NULL,
  authorization VARCHAR(80) NULL,
  reference_number VARCHAR(80) NULL,
  capture INT NULL,
  modality INT NULL,
  rv_number INT NULL,
  line_number INT NULL,
  status_audit INT NULL,
  installment INT NULL,
  transaction_status INT NULL,
  status_payment_bank INT NULL,
  transaction_status_reason INT NULL,
  mdr_rate DECIMAL(18,8) NULL,
  flex_rate DECIMAL(18,8) NULL,
  tip_value DECIMAL(18,8) NULL,
  gross_value DECIMAL(18,8) NULL,
  liquid_value DECIMAL(18,8) NULL,
  discount_value DECIMAL(18,8) NULL,
  first_installment_value DECIMAL(18,8) NULL,
  other_installments_value DECIMAL(18,8) NULL,
  flag_id BINARY(16) NULL,
  adjustment_id BINARY(16) NULL,
  acquirer_id BINARY(16) NULL,
  company_id BINARY(16) NULL,
  processed_file_id BINARY(16) NULL,
  sales_summary_id BINARY(16) NULL,
  establishment_id BINARY(16) NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_cs_transaction_acq_flag FOREIGN KEY (flag_id) REFERENCES cs_flag(id) ON UPDATE CASCADE,
  CONSTRAINT fk_cs_transaction_acq_acquirer FOREIGN KEY (acquirer_id) REFERENCES cs_acquirer(id) ON UPDATE CASCADE,
  CONSTRAINT fk_cs_transaction_acq_company FOREIGN KEY (company_id) REFERENCES cs_company(id) ON UPDATE CASCADE,
  CONSTRAINT fk_cs_transaction_acq_processed_file FOREIGN KEY (processed_file_id) REFERENCES cs_processed_file(id) ON UPDATE CASCADE,
  CONSTRAINT fk_cs_transaction_acq_sales_summary FOREIGN KEY (sales_summary_id) REFERENCES cs_sales_summary(id) ON UPDATE CASCADE,
  CONSTRAINT fk_cs_transaction_acq_establishment FOREIGN KEY (establishment_id) REFERENCES cs_establishment(id) ON UPDATE CASCADE,
  CONSTRAINT fk_cs_transaction_acq_created_by FOREIGN KEY (created_by_id) REFERENCES cs_users(id) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT fk_cs_transaction_acq_updated_by FOREIGN KEY (updated_by_id) REFERENCES cs_users(id) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB;

CREATE INDEX idx_cs_transaction_acq_nsu ON cs_transaction_acq(nsu);
CREATE INDEX idx_cs_transaction_acq_rv_number ON cs_transaction_acq(rv_number);
CREATE INDEX idx_cs_transaction_acq_sale_date ON cs_transaction_acq(sale_date);
CREATE INDEX idx_cs_transaction_acq_processed_file ON cs_transaction_acq(processed_file_id);

INSERT INTO cs_origin_file (id, created_at, code, name, description)
VALUES
  (UUID_TO_BIN(UUID()), CURRENT_TIMESTAMP(6), 'ERP', 'ERP', 'Arquivos CSV do ERP'),
  (UUID_TO_BIN(UUID()), CURRENT_TIMESTAMP(6), 'REDE', 'Rede', 'Arquivos da adquirente Rede')
ON DUPLICATE KEY UPDATE name = VALUES(name), description = VALUES(description);
