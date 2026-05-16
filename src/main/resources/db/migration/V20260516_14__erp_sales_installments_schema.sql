CREATE TABLE cs_transaction_erp (
  id BINARY(16) NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  created_by_id BINARY(16) NULL,
  updated_by_id BINARY(16) NULL,
  nsu BIGINT NULL,
  capture INT NULL,
  modality INT NULL,
  line_number INT NULL,
  installment INT NULL,
  status_transaction INT NULL,
  reason_exclusion_status INT NULL,
  status_transaction_reason INT NULL,
  tid VARCHAR(80) NULL,
  origin VARCHAR(120) NULL,
  machine VARCHAR(80) NULL,
  card_name VARCHAR(120) NULL,
  card_number VARCHAR(80) NULL,
  transaction_type VARCHAR(80) NULL,
  observations VARCHAR(500) NULL,
  authorization VARCHAR(80) NULL,
  installment_type VARCHAR(80) NULL,
  gross_value DECIMAL(18,8) NULL,
  liquid_value DECIMAL(18,8) NULL,
  discount_value DECIMAL(18,8) NULL,
  contracted_fee DECIMAL(18,8) NULL,
  canceled_date DATE NULL,
  sale_date DATETIME(6) NULL,
  deleted_date DATETIME(6) NULL,
  sale_reconciliation_date DATETIME(6) NULL,
  flag_id BINARY(16) NULL,
  acquirer_id BINARY(16) NULL,
  company_id BINARY(16) NULL,
  adjustment_id BINARY(16) NULL,
  processed_file_id BINARY(16) NULL,
  establishment_id BINARY(16) NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_cs_transaction_erp_flag FOREIGN KEY (flag_id) REFERENCES cs_flag(id) ON UPDATE CASCADE,
  CONSTRAINT fk_cs_transaction_erp_acquirer FOREIGN KEY (acquirer_id) REFERENCES cs_acquirer(id) ON UPDATE CASCADE,
  CONSTRAINT fk_cs_transaction_erp_company FOREIGN KEY (company_id) REFERENCES cs_company(id) ON UPDATE CASCADE,
  CONSTRAINT fk_cs_transaction_erp_processed_file FOREIGN KEY (processed_file_id) REFERENCES cs_processed_file(id) ON UPDATE CASCADE,
  CONSTRAINT fk_cs_transaction_erp_establishment FOREIGN KEY (establishment_id) REFERENCES cs_establishment(id) ON UPDATE CASCADE,
  CONSTRAINT fk_cs_transaction_erp_created_by FOREIGN KEY (created_by_id) REFERENCES cs_users(id) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT fk_cs_transaction_erp_updated_by FOREIGN KEY (updated_by_id) REFERENCES cs_users(id) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB;

CREATE INDEX idx_cs_transaction_erp_nsu ON cs_transaction_erp(nsu);
CREATE INDEX idx_cs_transaction_erp_sale_date ON cs_transaction_erp(sale_date);
CREATE INDEX idx_cs_transaction_erp_authorization ON cs_transaction_erp(authorization);
CREATE INDEX idx_cs_transaction_erp_processed_file ON cs_transaction_erp(processed_file_id);
CREATE INDEX idx_cs_transaction_erp_acquirer_flag ON cs_transaction_erp(acquirer_id, flag_id);

CREATE TABLE cs_installment_erp (
  id BINARY(16) NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  created_by_id BINARY(16) NULL,
  updated_by_id BINARY(16) NULL,
  gross_value DECIMAL(18,8) NULL,
  liquid_value DECIMAL(18,8) NULL,
  discount_value DECIMAL(18,8) NULL,
  installment INT NULL,
  status_payment_bank INT NULL,
  installment_status INT NULL,
  reconciliation_bank_line INT NULL,
  reconciliation_payment_line INT NULL,
  expected_payment_date DATE NULL,
  payment_date DATE NULL,
  cancellation_date DATE NULL,
  reconciliation_bank_processed_at DATETIME(6) NULL,
  reconciliation_payment_processed_at DATETIME(6) NULL,
  reconciliation_bank_file_id BINARY(16) NULL,
  reconciliation_payment_file_id BINARY(16) NULL,
  transaction_id BINARY(16) NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_cs_installment_erp_bank_file FOREIGN KEY (reconciliation_bank_file_id) REFERENCES cs_processed_file(id) ON UPDATE CASCADE,
  CONSTRAINT fk_cs_installment_erp_payment_file FOREIGN KEY (reconciliation_payment_file_id) REFERENCES cs_processed_file(id) ON UPDATE CASCADE,
  CONSTRAINT fk_cs_installment_erp_transaction FOREIGN KEY (transaction_id) REFERENCES cs_transaction_erp(id) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT fk_cs_installment_erp_created_by FOREIGN KEY (created_by_id) REFERENCES cs_users(id) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT fk_cs_installment_erp_updated_by FOREIGN KEY (updated_by_id) REFERENCES cs_users(id) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB;

CREATE INDEX idx_cs_installment_erp_transaction ON cs_installment_erp(transaction_id);
CREATE INDEX idx_cs_installment_erp_expected_payment_date ON cs_installment_erp(expected_payment_date);
CREATE INDEX idx_cs_installment_erp_payment_date ON cs_installment_erp(payment_date);
CREATE INDEX idx_cs_installment_erp_status ON cs_installment_erp(status_payment_bank, installment_status);

ALTER TABLE cs_transaction_erp ADD COLUMN transaction_acq_id BINARY(16) NULL AFTER adjustment_id;
CREATE INDEX idx_cs_transaction_erp_transaction_acq ON cs_transaction_erp (transaction_acq_id);
ALTER TABLE cs_transaction_erp ADD CONSTRAINT fk_cs_transaction_erp_transaction_acq FOREIGN KEY (transaction_acq_id) REFERENCES cs_transaction_acq (id) ON UPDATE CASCADE;

ALTER TABLE cs_transaction_erp
  ADD COLUMN commercial_status VARCHAR(40) NULL AFTER contracted_fee,
  ADD COLUMN commercial_status_message VARCHAR(500) NULL AFTER commercial_status;

CREATE INDEX idx_cs_transaction_erp_commercial_status ON cs_transaction_erp(commercial_status);

ALTER TABLE cs_transaction_erp
  ADD COLUMN source_company_cnpj VARCHAR(32) NULL AFTER authorization,
  ADD COLUMN source_company_name VARCHAR(255) NULL AFTER source_company_cnpj,
  ADD COLUMN source_establishment_pv_number INT NULL AFTER source_company_name;

CREATE INDEX idx_cs_transaction_erp_source_company_cnpj ON cs_transaction_erp(source_company_cnpj);
CREATE INDEX idx_cs_transaction_erp_source_establishment_pv ON cs_transaction_erp(source_establishment_pv_number);

ALTER TABLE cs_transaction_erp
  ADD COLUMN banking_domicile_id BINARY(16) NULL AFTER company_id,
  ADD CONSTRAINT fk_cs_transaction_erp_banking_domicile
    FOREIGN KEY (banking_domicile_id) REFERENCES cs_banking_domicile(id) ON UPDATE CASCADE;

CREATE INDEX idx_cs_transaction_erp_banking_domicile ON cs_transaction_erp(banking_domicile_id);
