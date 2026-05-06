ALTER TABLE cs_serasa_consultation
  ADD COLUMN service_type VARCHAR(40) NULL;

CREATE INDEX idx_cs_serasa_consultation_service_type ON cs_serasa_consultation(service_type);

ALTER TABLE cs_pending_debt
  ADD COLUMN payment_date DATE NULL,
  ADD COLUMN pending_value DECIMAL(18,8) NULL,
  ADD COLUMN retention_process_number BIGINT NULL,
  ADD COLUMN compensation_code INT NULL,
  ADD COLUMN compensation_description VARCHAR(255) NULL,
  ADD COLUMN reason_code2 INT NULL;

ALTER TABLE cs_settled_debt
  MODIFY COLUMN nsu BIGINT NULL,
  MODIFY COLUMN letter_number BIGINT NULL,
  MODIFY COLUMN number_debit_order BIGINT NULL,
  MODIFY COLUMN retention_process_number BIGINT NULL;

ALTER TABLE cs_installment_unscheduling
  ADD COLUMN original_installment_number INT NULL,
  ADD COLUMN adjusted_installment_number INT NULL,
  ADD COLUMN adjusted_pv_number INT NULL,
  ADD COLUMN adjusted_rv_number INT NULL,
  ADD COLUMN negotiation_type INT NULL,
  ADD COLUMN rv_date_original DATE NULL,
  ADD COLUMN adjusted_credit_date DATE NULL,
  ADD COLUMN adjusted_rv_date DATE NULL,
  ADD COLUMN negotiation_date DATE NULL,
  ADD COLUMN negotiation_contract_number BIGINT NULL,
  ADD COLUMN partner_cnpj VARCHAR(20) NULL,
  ADD COLUMN flag_rv_adjusted_id BINARY(16) NULL;

ALTER TABLE cs_installment_unscheduling
  ADD CONSTRAINT fk_cs_installment_unscheduling_flag_adjusted FOREIGN KEY (flag_rv_adjusted_id) REFERENCES cs_flag(id) ON UPDATE CASCADE;

CREATE INDEX idx_cs_installment_unscheduling_adjusted_rv ON cs_installment_unscheduling(adjusted_pv_number, adjusted_rv_number);

CREATE TABLE cs_rede_pix_cancellation (
  id BINARY(16) NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  created_by_id BINARY(16) NULL,
  updated_by_id BINARY(16) NULL,
  record_type VARCHAR(20) NULL,
  line_number INT NULL,
  pv_number INT NULL,
  debit_order_number BIGINT NULL,
  internal_charge_id VARCHAR(40) NULL,
  acquirer_id BINARY(16) NULL,
  company_id BINARY(16) NULL,
  establishment_id BINARY(16) NULL,
  processed_file_id BINARY(16) NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_cs_rede_pix_cancellation_acquirer FOREIGN KEY (acquirer_id) REFERENCES cs_acquirer(id) ON UPDATE CASCADE,
  CONSTRAINT fk_cs_rede_pix_cancellation_company FOREIGN KEY (company_id) REFERENCES cs_company(id) ON UPDATE CASCADE,
  CONSTRAINT fk_cs_rede_pix_cancellation_establishment FOREIGN KEY (establishment_id) REFERENCES cs_establishment(id) ON UPDATE CASCADE,
  CONSTRAINT fk_cs_rede_pix_cancellation_processed_file FOREIGN KEY (processed_file_id) REFERENCES cs_processed_file(id) ON UPDATE CASCADE,
  CONSTRAINT fk_cs_rede_pix_cancellation_created_by FOREIGN KEY (created_by_id) REFERENCES cs_users(id) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT fk_cs_rede_pix_cancellation_updated_by FOREIGN KEY (updated_by_id) REFERENCES cs_users(id) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB;
CREATE INDEX idx_cs_rede_pix_cancellation_pv ON cs_rede_pix_cancellation(pv_number);

CREATE TABLE cs_rede_suspended_payment (
  id BINARY(16) NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  created_by_id BINARY(16) NULL,
  updated_by_id BINARY(16) NULL,
  record_type VARCHAR(20) NULL,
  line_number INT NULL,
  pv_number INT NULL,
  credit_order_number BIGINT NULL,
  credit_order_value DECIMAL(18,8) NULL,
  release_date DATE NULL,
  original_due_date DATE NULL,
  rv_number INT NULL,
  rv_date DATE NULL,
  suspension_date DATE NULL,
  payment_type VARCHAR(20) NULL,
  flag_code VARCHAR(10) NULL,
  rede_contract_number BIGINT NULL,
  contract_update_date DATE NULL,
  installment_number INT NULL,
  original_contract_date DATE NULL,
  cip_contract_number VARCHAR(30) NULL,
  flag_id BINARY(16) NULL,
  acquirer_id BINARY(16) NULL,
  company_id BINARY(16) NULL,
  establishment_id BINARY(16) NULL,
  sales_summary_id BINARY(16) NULL,
  processed_file_id BINARY(16) NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_cs_rede_suspended_payment_flag FOREIGN KEY (flag_id) REFERENCES cs_flag(id) ON UPDATE CASCADE,
  CONSTRAINT fk_cs_rede_suspended_payment_acquirer FOREIGN KEY (acquirer_id) REFERENCES cs_acquirer(id) ON UPDATE CASCADE,
  CONSTRAINT fk_cs_rede_suspended_payment_company FOREIGN KEY (company_id) REFERENCES cs_company(id) ON UPDATE CASCADE,
  CONSTRAINT fk_cs_rede_suspended_payment_establishment FOREIGN KEY (establishment_id) REFERENCES cs_establishment(id) ON UPDATE CASCADE,
  CONSTRAINT fk_cs_rede_suspended_payment_summary FOREIGN KEY (sales_summary_id) REFERENCES cs_sales_summary(id) ON UPDATE CASCADE,
  CONSTRAINT fk_cs_rede_suspended_payment_processed_file FOREIGN KEY (processed_file_id) REFERENCES cs_processed_file(id) ON UPDATE CASCADE,
  CONSTRAINT fk_cs_rede_suspended_payment_created_by FOREIGN KEY (created_by_id) REFERENCES cs_users(id) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT fk_cs_rede_suspended_payment_updated_by FOREIGN KEY (updated_by_id) REFERENCES cs_users(id) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB;
CREATE INDEX idx_cs_rede_suspended_payment_pv_rv ON cs_rede_suspended_payment(pv_number, rv_number);

CREATE TABLE cs_rede_technical_reserve (
  id BINARY(16) NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  created_by_id BINARY(16) NULL,
  updated_by_id BINARY(16) NULL,
  record_type VARCHAR(20) NULL,
  line_number INT NULL,
  pv_number INT NULL,
  rv_number_original INT NULL,
  rv_date_original DATE NULL,
  flag_code VARCHAR(10) NULL,
  installment_number INT NULL,
  due_date DATE NULL,
  credit_order_number BIGINT NULL,
  credit_order_reference_number BIGINT NULL,
  credit_order_value DECIMAL(18,8) NULL,
  reserve_inclusion_date DATE NULL,
  reserve_exclusion_date DATE NULL,
  bank INT NULL,
  agency INT NULL,
  account BIGINT NULL,
  reserve_status INT NULL,
  flag_id BINARY(16) NULL,
  acquirer_id BINARY(16) NULL,
  company_id BINARY(16) NULL,
  establishment_id BINARY(16) NULL,
  sales_summary_id BINARY(16) NULL,
  processed_file_id BINARY(16) NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_cs_rede_technical_reserve_flag FOREIGN KEY (flag_id) REFERENCES cs_flag(id) ON UPDATE CASCADE,
  CONSTRAINT fk_cs_rede_technical_reserve_acquirer FOREIGN KEY (acquirer_id) REFERENCES cs_acquirer(id) ON UPDATE CASCADE,
  CONSTRAINT fk_cs_rede_technical_reserve_company FOREIGN KEY (company_id) REFERENCES cs_company(id) ON UPDATE CASCADE,
  CONSTRAINT fk_cs_rede_technical_reserve_establishment FOREIGN KEY (establishment_id) REFERENCES cs_establishment(id) ON UPDATE CASCADE,
  CONSTRAINT fk_cs_rede_technical_reserve_summary FOREIGN KEY (sales_summary_id) REFERENCES cs_sales_summary(id) ON UPDATE CASCADE,
  CONSTRAINT fk_cs_rede_technical_reserve_processed_file FOREIGN KEY (processed_file_id) REFERENCES cs_processed_file(id) ON UPDATE CASCADE,
  CONSTRAINT fk_cs_rede_technical_reserve_created_by FOREIGN KEY (created_by_id) REFERENCES cs_users(id) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT fk_cs_rede_technical_reserve_updated_by FOREIGN KEY (updated_by_id) REFERENCES cs_users(id) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB;
CREATE INDEX idx_cs_rede_technical_reserve_pv_rv ON cs_rede_technical_reserve(pv_number, rv_number_original);
