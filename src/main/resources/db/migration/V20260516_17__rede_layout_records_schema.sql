CREATE TABLE cs_pv_matrix_header (
  id UUID NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NULL DEFAULT CURRENT_TIMESTAMP(6),
  created_by_id UUID NULL,
  updated_by_id UUID NULL,
  pv_number INT NULL,
  line_number INT NULL,
  record_type VARCHAR(20) NULL,
  commercial_name VARCHAR(120) NULL,
  acquirer_id UUID NULL,
  company_id UUID NULL,
  establishment_id UUID NULL,
  processed_file_id UUID NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_cs_pv_matrix_header_acquirer FOREIGN KEY (acquirer_id) REFERENCES cs_acquirer(id) ON UPDATE CASCADE,
  CONSTRAINT fk_cs_pv_matrix_header_company FOREIGN KEY (company_id) REFERENCES cs_company(id) ON UPDATE CASCADE,
  CONSTRAINT fk_cs_pv_matrix_header_establishment FOREIGN KEY (establishment_id) REFERENCES cs_establishment(id) ON UPDATE CASCADE,
  CONSTRAINT fk_cs_pv_matrix_header_processed_file FOREIGN KEY (processed_file_id) REFERENCES cs_processed_file(id) ON UPDATE CASCADE
);
CREATE INDEX idx_cs_pv_matrix_header_pv ON cs_pv_matrix_header(pv_number);

CREATE TABLE cs_serasa_consultation (
  id UUID NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NULL DEFAULT CURRENT_TIMESTAMP(6),
  created_by_id UUID NULL,
  updated_by_id UUID NULL,
  pv_number INT NULL,
  line_number INT NULL,
  record_type VARCHAR(20) NULL,
  number_consultation_carried_out INT NULL,
  total_value_consultation DECIMAL(18,8) NULL,
  start_consultation_period DATE NULL,
  end_consultation_period DATE NULL,
  value_consultation_period DECIMAL(18,8) NULL,
  acquirer_id UUID NULL,
  company_id UUID NULL,
  establishment_id UUID NULL,
  processed_file_id UUID NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_cs_serasa_consultation_acquirer FOREIGN KEY (acquirer_id) REFERENCES cs_acquirer(id) ON UPDATE CASCADE,
  CONSTRAINT fk_cs_serasa_consultation_company FOREIGN KEY (company_id) REFERENCES cs_company(id) ON UPDATE CASCADE,
  CONSTRAINT fk_cs_serasa_consultation_establishment FOREIGN KEY (establishment_id) REFERENCES cs_establishment(id) ON UPDATE CASCADE,
  CONSTRAINT fk_cs_serasa_consultation_processed_file FOREIGN KEY (processed_file_id) REFERENCES cs_processed_file(id) ON UPDATE CASCADE
);
CREATE INDEX idx_cs_serasa_consultation_pv_period ON cs_serasa_consultation(pv_number, start_consultation_period, end_consultation_period);

CREATE TABLE cs_pending_debt (
  id UUID NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NULL DEFAULT CURRENT_TIMESTAMP(6),
  created_by_id UUID NULL,
  updated_by_id UUID NULL,
  tid VARCHAR(80) NULL,
  nsu BIGINT NULL,
  pv_number INT NULL,
  record_type VARCHAR(20) NULL,
  card_number VARCHAR(80) NULL,
  line_number INT NULL,
  reason_code INT NULL,
  "authorization" VARCHAR(80) NULL,
  letter_number BIGINT NULL,
  letter_date DATE NULL,
  reference_month VARCHAR(20) NULL,
  reason_description VARCHAR(255) NULL,
  pv_number_original INT NULL,
  number_rv_original INT NULL,
  date_rv_original DATE NULL,
  number_debit_order BIGINT NULL,
  date_debit_order DATE NULL,
  value_debit_order DECIMAL(18,8) NULL,
  compensated_value DECIMAL(18,8) NULL,
  number_process_chargeback BIGINT NULL,
  date_original_transaction DATE NULL,
  original_transaction_value DECIMAL(18,8) NULL,
  flag_id UUID NULL,
  acquirer_id UUID NULL,
  company_id UUID NULL,
  establishment_id UUID NULL,
  processed_file_id UUID NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_cs_pending_debt_flag FOREIGN KEY (flag_id) REFERENCES cs_flag(id) ON UPDATE CASCADE,
  CONSTRAINT fk_cs_pending_debt_acquirer FOREIGN KEY (acquirer_id) REFERENCES cs_acquirer(id) ON UPDATE CASCADE,
  CONSTRAINT fk_cs_pending_debt_company FOREIGN KEY (company_id) REFERENCES cs_company(id) ON UPDATE CASCADE,
  CONSTRAINT fk_cs_pending_debt_establishment FOREIGN KEY (establishment_id) REFERENCES cs_establishment(id) ON UPDATE CASCADE,
  CONSTRAINT fk_cs_pending_debt_processed_file FOREIGN KEY (processed_file_id) REFERENCES cs_processed_file(id) ON UPDATE CASCADE
);
CREATE INDEX idx_cs_pending_debt_pv_date ON cs_pending_debt(pv_number, date_debit_order);
CREATE INDEX idx_cs_pending_debt_nsu ON cs_pending_debt(nsu);

CREATE TABLE cs_installment_unscheduling (
  id UUID NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NULL DEFAULT CURRENT_TIMESTAMP(6),
  created_by_id UUID NULL,
  updated_by_id UUID NULL,
  nsu BIGINT NULL,
  tid VARCHAR(80) NULL,
  order_number VARCHAR(80) NULL,
  record_type VARCHAR(20) NULL,
  card_number VARCHAR(80) NULL,
  type_debit VARCHAR(30) NULL,
  line_number INT NULL,
  number_installment INT NULL,
  pv_number_original INT NULL,
  rv_number_original INT NULL,
  unscheduling_status INT NULL,
  reference_number VARCHAR(80) NULL,
  ecommerce BOOLEAN NULL,
  date_credit DATE NULL,
  cancellation_date DATE NULL,
  transaction_date DATE NULL,
  rv_value_original DECIMAL(18,8) NULL,
  adjustment_value DECIMAL(18,8) NULL,
  cancellation_value DECIMAL(18,8) NULL,
  new_installment_value DECIMAL(18,8) NULL,
  original_value_changed_installment DECIMAL(18,8) NULL,
  flag_rv_origin_id UUID NULL,
  acquirer_id UUID NULL,
  company_id UUID NULL,
  establishment_id UUID NULL,
  processed_file_id UUID NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_cs_installment_unscheduling_flag FOREIGN KEY (flag_rv_origin_id) REFERENCES cs_flag(id) ON UPDATE CASCADE,
  CONSTRAINT fk_cs_installment_unscheduling_acquirer FOREIGN KEY (acquirer_id) REFERENCES cs_acquirer(id) ON UPDATE CASCADE,
  CONSTRAINT fk_cs_installment_unscheduling_company FOREIGN KEY (company_id) REFERENCES cs_company(id) ON UPDATE CASCADE,
  CONSTRAINT fk_cs_installment_unscheduling_establishment FOREIGN KEY (establishment_id) REFERENCES cs_establishment(id) ON UPDATE CASCADE,
  CONSTRAINT fk_cs_installment_unscheduling_processed_file FOREIGN KEY (processed_file_id) REFERENCES cs_processed_file(id) ON UPDATE CASCADE
);
CREATE INDEX idx_cs_installment_unscheduling_rv ON cs_installment_unscheduling(pv_number_original, rv_number_original);
CREATE INDEX idx_cs_installment_unscheduling_nsu ON cs_installment_unscheduling(nsu);

CREATE TABLE cs_totalizer_matrix (
  id UUID NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NULL DEFAULT CURRENT_TIMESTAMP(6),
  created_by_id UUID NULL,
  updated_by_id UUID NULL,
  pv_number INT NULL,
  line_number INT NULL,
  record_type VARCHAR(20) NULL,
  total_number_matrix_summaries INT NULL,
  total_value_normal_credits DECIMAL(18,8) NULL,
  value_advance_credits INT NULL,
  total_value_anticipated DECIMAL(18,8) NULL,
  amount_credit_adjustments INT NULL,
  total_value_credit_adjustments DECIMAL(18,8) NULL,
  amount_debit_adjustments INT NULL,
  total_value_debit_adjustments DECIMAL(18,8) NULL,
  acquirer_id UUID NULL,
  company_id UUID NULL,
  establishment_id UUID NULL,
  processed_file_id UUID NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_cs_totalizer_matrix_acquirer FOREIGN KEY (acquirer_id) REFERENCES cs_acquirer(id) ON UPDATE CASCADE,
  CONSTRAINT fk_cs_totalizer_matrix_company FOREIGN KEY (company_id) REFERENCES cs_company(id) ON UPDATE CASCADE,
  CONSTRAINT fk_cs_totalizer_matrix_establishment FOREIGN KEY (establishment_id) REFERENCES cs_establishment(id) ON UPDATE CASCADE,
  CONSTRAINT fk_cs_totalizer_matrix_processed_file FOREIGN KEY (processed_file_id) REFERENCES cs_processed_file(id) ON UPDATE CASCADE
);
CREATE INDEX idx_cs_totalizer_matrix_pv ON cs_totalizer_matrix(pv_number);

CREATE TABLE cs_archive_trailer (
  id UUID NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NULL DEFAULT CURRENT_TIMESTAMP(6),
  created_by_id UUID NULL,
  updated_by_id UUID NULL,
  line_number INT NULL,
  record_type VARCHAR(20) NULL,
  number_matrices INT NULL,
  number_records INT NULL,
  pv_requesting INT NULL,
  normal_credits_quantity INT NULL,
  total_value_rv DECIMAL(18,8) NULL,
  advance_credit_amount INT NULL,
  total_value_upfront DECIMAL(18,8) NULL,
  amount_credit_adjustments INT NULL,
  total_value_credit_adjustments DECIMAL(18,8) NULL,
  debit_adjustment_quantity INT NULL,
  total_value_debit DECIMAL(18,8) NULL,
  acquirer_id UUID NULL,
  company_id UUID NULL,
  establishment_id UUID NULL,
  processed_file_id UUID NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_cs_archive_trailer_acquirer FOREIGN KEY (acquirer_id) REFERENCES cs_acquirer(id) ON UPDATE CASCADE,
  CONSTRAINT fk_cs_archive_trailer_company FOREIGN KEY (company_id) REFERENCES cs_company(id) ON UPDATE CASCADE,
  CONSTRAINT fk_cs_archive_trailer_establishment FOREIGN KEY (establishment_id) REFERENCES cs_establishment(id) ON UPDATE CASCADE,
  CONSTRAINT fk_cs_archive_trailer_processed_file FOREIGN KEY (processed_file_id) REFERENCES cs_processed_file(id) ON UPDATE CASCADE
);
CREATE INDEX idx_cs_archive_trailer_file ON cs_archive_trailer(processed_file_id);

CREATE TABLE cs_rede_request_notice (
  id UUID NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NULL DEFAULT CURRENT_TIMESTAMP(6),
  created_by_id UUID NULL,
  updated_by_id UUID NULL,
  line_number INT NULL,
  record_type VARCHAR(20) NULL,
  pv_number INT NULL,
  rv_number INT NULL,
  card_number VARCHAR(80) NULL,
  transaction_value DECIMAL(18,8) NULL,
  sale_date DATE NULL,
  reference_number DECIMAL(38,0) NULL,
  process_number DECIMAL(38,0) NULL,
  nsu BIGINT NULL,
  "authorization" VARCHAR(80) NULL,
  request_code INT NULL,
  deadline DATE NULL,
  request_status INT NULL,
  flag_id UUID NULL,
  acquirer_id UUID NULL,
  company_id UUID NULL,
  establishment_id UUID NULL,
  sales_summary_id UUID NULL,
  processed_file_id UUID NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_cs_rede_request_notice_flag FOREIGN KEY (flag_id) REFERENCES cs_flag(id) ON UPDATE CASCADE,
  CONSTRAINT fk_cs_rede_request_notice_acquirer FOREIGN KEY (acquirer_id) REFERENCES cs_acquirer(id) ON UPDATE CASCADE,
  CONSTRAINT fk_cs_rede_request_notice_company FOREIGN KEY (company_id) REFERENCES cs_company(id) ON UPDATE CASCADE,
  CONSTRAINT fk_cs_rede_request_notice_establishment FOREIGN KEY (establishment_id) REFERENCES cs_establishment(id) ON UPDATE CASCADE,
  CONSTRAINT fk_cs_rede_request_notice_sales_summary FOREIGN KEY (sales_summary_id) REFERENCES cs_sales_summary(id) ON UPDATE CASCADE,
  CONSTRAINT fk_cs_rede_request_notice_processed_file FOREIGN KEY (processed_file_id) REFERENCES cs_processed_file(id) ON UPDATE CASCADE
);

CREATE INDEX idx_cs_rede_request_notice_pv_rv ON cs_rede_request_notice(pv_number, rv_number);
CREATE INDEX idx_cs_rede_request_notice_nsu ON cs_rede_request_notice(nsu);
CREATE INDEX idx_cs_rede_request_notice_processed_file ON cs_rede_request_notice(processed_file_id);

CREATE TABLE cs_rede_eevd_totalizer (
  id UUID NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NULL DEFAULT CURRENT_TIMESTAMP(6),
  created_by_id UUID NULL,
  updated_by_id UUID NULL,
  record_type VARCHAR(20) NULL,
  line_number INT NULL,
  pv_number INT NULL,
  matrix_number INT NULL,
  sales_summary_quantity INT NULL,
  sales_receipt_quantity INT NULL,
  total_gross_value DECIMAL(18,8) NULL,
  total_discount_value DECIMAL(18,8) NULL,
  total_liquid_value DECIMAL(18,8) NULL,
  predating_gross_value DECIMAL(18,8) NULL,
  predating_discount_value DECIMAL(18,8) NULL,
  predating_liquid_value DECIMAL(18,8) NULL,
  total_file_records INT NULL,
  acquirer_id UUID NULL,
  company_id UUID NULL,
  establishment_id UUID NULL,
  processed_file_id UUID NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_cs_rede_eevd_totalizer_acquirer FOREIGN KEY (acquirer_id) REFERENCES cs_acquirer(id) ON UPDATE CASCADE,
  CONSTRAINT fk_cs_rede_eevd_totalizer_company FOREIGN KEY (company_id) REFERENCES cs_company(id) ON UPDATE CASCADE,
  CONSTRAINT fk_cs_rede_eevd_totalizer_establishment FOREIGN KEY (establishment_id) REFERENCES cs_establishment(id) ON UPDATE CASCADE,
  CONSTRAINT fk_cs_rede_eevd_totalizer_processed_file FOREIGN KEY (processed_file_id) REFERENCES cs_processed_file(id) ON UPDATE CASCADE
);
CREATE INDEX idx_cs_rede_eevd_totalizer_file_type ON cs_rede_eevd_totalizer(processed_file_id, record_type);
CREATE INDEX idx_cs_rede_eevd_totalizer_pv ON cs_rede_eevd_totalizer(pv_number);

CREATE TABLE cs_rede_negotiated_transaction (
  id UUID NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NULL DEFAULT CURRENT_TIMESTAMP(6),
  created_by_id UUID NULL,
  updated_by_id UUID NULL,
  record_type VARCHAR(20) NULL,
  line_number INT NULL,
  establishment_number INT NULL,
  rv_number INT NULL,
  sale_date DATE NULL,
  rv_credit_date DATE NULL,
  transaction_type VARCHAR(10) NULL,
  flag_code VARCHAR(10) NULL,
  negotiation_type INT NULL,
  settlement_summary_number BIGINT NULL,
  settlement_summary_date DATE NULL,
  settlement_summary_value DECIMAL(18,8) NULL,
  negotiation_contract_number BIGINT NULL,
  partner_cnpj VARCHAR(20) NULL,
  generated_rl_document_number BIGINT NULL,
  negotiated_value DECIMAL(18,8) NULL,
  negotiation_date DATE NULL,
  liquidation_date DATE NULL,
  bank INT NULL,
  agency INT NULL,
  account BIGINT NULL,
  credit_status INT NULL,
  flag_id UUID NULL,
  acquirer_id UUID NULL,
  company_id UUID NULL,
  establishment_id UUID NULL,
  sales_summary_id UUID NULL,
  processed_file_id UUID NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_cs_rede_negotiated_flag FOREIGN KEY (flag_id) REFERENCES cs_flag(id) ON UPDATE CASCADE,
  CONSTRAINT fk_cs_rede_negotiated_acquirer FOREIGN KEY (acquirer_id) REFERENCES cs_acquirer(id) ON UPDATE CASCADE,
  CONSTRAINT fk_cs_rede_negotiated_company FOREIGN KEY (company_id) REFERENCES cs_company(id) ON UPDATE CASCADE,
  CONSTRAINT fk_cs_rede_negotiated_establishment FOREIGN KEY (establishment_id) REFERENCES cs_establishment(id) ON UPDATE CASCADE,
  CONSTRAINT fk_cs_rede_negotiated_sales_summary FOREIGN KEY (sales_summary_id) REFERENCES cs_sales_summary(id) ON UPDATE CASCADE,
  CONSTRAINT fk_cs_rede_negotiated_processed_file FOREIGN KEY (processed_file_id) REFERENCES cs_processed_file(id) ON UPDATE CASCADE
);
CREATE INDEX idx_cs_rede_negotiated_rv ON cs_rede_negotiated_transaction(establishment_number, rv_number);
CREATE INDEX idx_cs_rede_negotiated_file ON cs_rede_negotiated_transaction(processed_file_id);
CREATE INDEX idx_cs_rede_negotiated_liquidation ON cs_rede_negotiated_transaction(liquidation_date);

CREATE TABLE cs_rede_ic_plus_transaction (
  id UUID NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NULL DEFAULT CURRENT_TIMESTAMP(6),
  created_by_id UUID NULL,
  updated_by_id UUID NULL,
  record_type VARCHAR(20) NULL,
  line_number INT NULL,
  pv_number INT NULL,
  rv_number_original INT NULL,
  rv_date_original DATE NULL,
  nsu BIGINT NULL,
  transaction_date DATE NULL,
  mcc INT NULL,
  card_profile VARCHAR(3) NULL,
  interchange_value DECIMAL(18,8) NULL,
  plus_value DECIMAL(18,8) NULL,
  entry_mode VARCHAR(10) NULL,
  acquirer_id UUID NULL,
  company_id UUID NULL,
  establishment_id UUID NULL,
  sales_summary_id UUID NULL,
  processed_file_id UUID NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_cs_rede_ic_plus_acquirer FOREIGN KEY (acquirer_id) REFERENCES cs_acquirer(id) ON UPDATE CASCADE,
  CONSTRAINT fk_cs_rede_ic_plus_company FOREIGN KEY (company_id) REFERENCES cs_company(id) ON UPDATE CASCADE,
  CONSTRAINT fk_cs_rede_ic_plus_establishment FOREIGN KEY (establishment_id) REFERENCES cs_establishment(id) ON UPDATE CASCADE,
  CONSTRAINT fk_cs_rede_ic_plus_sales_summary FOREIGN KEY (sales_summary_id) REFERENCES cs_sales_summary(id) ON UPDATE CASCADE,
  CONSTRAINT fk_cs_rede_ic_plus_processed_file FOREIGN KEY (processed_file_id) REFERENCES cs_processed_file(id) ON UPDATE CASCADE
);
CREATE INDEX idx_cs_rede_ic_plus_rv ON cs_rede_ic_plus_transaction(pv_number, rv_number_original);
CREATE INDEX idx_cs_rede_ic_plus_nsu ON cs_rede_ic_plus_transaction(nsu);
CREATE INDEX idx_cs_rede_ic_plus_file ON cs_rede_ic_plus_transaction(processed_file_id);

ALTER TABLE cs_totalizer_matrix
  ADD COLUMN total_gross_value DECIMAL(18,8) NULL,
  ADD COLUMN rejected_cv_nsu_quantity INT NULL,
  ADD COLUMN total_rejected_value DECIMAL(18,8) NULL,
  ADD COLUMN total_rotating_value DECIMAL(18,8) NULL,
  ADD COLUMN total_installment_value DECIMAL(18,8) NULL,
  ADD COLUMN total_iata_value DECIMAL(18,8) NULL,
  ADD COLUMN total_dollar_value DECIMAL(18,8) NULL,
  ADD COLUMN total_discount_value DECIMAL(18,8) NULL,
  ADD COLUMN total_liquid_value DECIMAL(18,8) NULL,
  ADD COLUMN total_tip_value DECIMAL(18,8) NULL,
  ADD COLUMN total_boarding_fee_value DECIMAL(18,8) NULL,
  ADD COLUMN accepted_cv_nsu_quantity INT NULL;

ALTER TABLE cs_archive_trailer
  ADD COLUMN total_gross_value DECIMAL(18,8) NULL,
  ADD COLUMN rejected_cv_nsu_quantity INT NULL,
  ADD COLUMN total_rejected_value DECIMAL(18,8) NULL,
  ADD COLUMN total_rotating_value DECIMAL(18,8) NULL,
  ADD COLUMN total_installment_value DECIMAL(18,8) NULL,
  ADD COLUMN total_iata_value DECIMAL(18,8) NULL,
  ADD COLUMN total_dollar_value DECIMAL(18,8) NULL,
  ADD COLUMN total_discount_value DECIMAL(18,8) NULL,
  ADD COLUMN total_liquid_value DECIMAL(18,8) NULL,
  ADD COLUMN total_tip_value DECIMAL(18,8) NULL,
  ADD COLUMN total_boarding_fee_value DECIMAL(18,8) NULL,
  ADD COLUMN accepted_cv_nsu_quantity INT NULL;

ALTER TABLE cs_rede_request_notice
  ADD COLUMN tid VARCHAR(20) NULL,
  ADD COLUMN ecommerce_order_number VARCHAR(30) NULL;

ALTER TABLE cs_serasa_consultation
  ADD COLUMN flag_id UUID NULL,
  ADD CONSTRAINT fk_cs_serasa_consultation_flag FOREIGN KEY (flag_id) REFERENCES cs_flag(id) ON UPDATE CASCADE;

CREATE INDEX idx_cs_rede_request_notice_tid ON cs_rede_request_notice(tid);
CREATE INDEX idx_cs_serasa_consultation_flag ON cs_serasa_consultation(flag_id);

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
  ALTER COLUMN nsu TYPE BIGINT,
  ALTER COLUMN letter_number TYPE BIGINT,
  ALTER COLUMN number_debit_order TYPE BIGINT,
  ALTER COLUMN retention_process_number TYPE BIGINT;

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
  ADD COLUMN flag_rv_adjusted_id UUID NULL;

ALTER TABLE cs_installment_unscheduling
  ADD CONSTRAINT fk_cs_installment_unscheduling_flag_adjusted FOREIGN KEY (flag_rv_adjusted_id) REFERENCES cs_flag(id) ON UPDATE CASCADE;

CREATE INDEX idx_cs_installment_unscheduling_adjusted_rv ON cs_installment_unscheduling(adjusted_pv_number, adjusted_rv_number);

CREATE TABLE cs_rede_pix_cancellation (
  id UUID NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NULL DEFAULT CURRENT_TIMESTAMP(6),
  created_by_id UUID NULL,
  updated_by_id UUID NULL,
  record_type VARCHAR(20) NULL,
  line_number INT NULL,
  pv_number INT NULL,
  debit_order_number BIGINT NULL,
  internal_charge_id VARCHAR(40) NULL,
  acquirer_id UUID NULL,
  company_id UUID NULL,
  establishment_id UUID NULL,
  processed_file_id UUID NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_cs_rede_pix_cancellation_acquirer FOREIGN KEY (acquirer_id) REFERENCES cs_acquirer(id) ON UPDATE CASCADE,
  CONSTRAINT fk_cs_rede_pix_cancellation_company FOREIGN KEY (company_id) REFERENCES cs_company(id) ON UPDATE CASCADE,
  CONSTRAINT fk_cs_rede_pix_cancellation_establishment FOREIGN KEY (establishment_id) REFERENCES cs_establishment(id) ON UPDATE CASCADE,
  CONSTRAINT fk_cs_rede_pix_cancellation_processed_file FOREIGN KEY (processed_file_id) REFERENCES cs_processed_file(id) ON UPDATE CASCADE
);
CREATE INDEX idx_cs_rede_pix_cancellation_pv ON cs_rede_pix_cancellation(pv_number);

CREATE TABLE cs_rede_suspended_payment (
  id UUID NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NULL DEFAULT CURRENT_TIMESTAMP(6),
  created_by_id UUID NULL,
  updated_by_id UUID NULL,
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
  flag_id UUID NULL,
  acquirer_id UUID NULL,
  company_id UUID NULL,
  establishment_id UUID NULL,
  sales_summary_id UUID NULL,
  processed_file_id UUID NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_cs_rede_suspended_payment_flag FOREIGN KEY (flag_id) REFERENCES cs_flag(id) ON UPDATE CASCADE,
  CONSTRAINT fk_cs_rede_suspended_payment_acquirer FOREIGN KEY (acquirer_id) REFERENCES cs_acquirer(id) ON UPDATE CASCADE,
  CONSTRAINT fk_cs_rede_suspended_payment_company FOREIGN KEY (company_id) REFERENCES cs_company(id) ON UPDATE CASCADE,
  CONSTRAINT fk_cs_rede_suspended_payment_establishment FOREIGN KEY (establishment_id) REFERENCES cs_establishment(id) ON UPDATE CASCADE,
  CONSTRAINT fk_cs_rede_suspended_payment_summary FOREIGN KEY (sales_summary_id) REFERENCES cs_sales_summary(id) ON UPDATE CASCADE,
  CONSTRAINT fk_cs_rede_suspended_payment_processed_file FOREIGN KEY (processed_file_id) REFERENCES cs_processed_file(id) ON UPDATE CASCADE
);
CREATE INDEX idx_cs_rede_suspended_payment_pv_rv ON cs_rede_suspended_payment(pv_number, rv_number);

CREATE TABLE cs_rede_technical_reserve (
  id UUID NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NULL DEFAULT CURRENT_TIMESTAMP(6),
  created_by_id UUID NULL,
  updated_by_id UUID NULL,
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
  flag_id UUID NULL,
  acquirer_id UUID NULL,
  company_id UUID NULL,
  establishment_id UUID NULL,
  sales_summary_id UUID NULL,
  processed_file_id UUID NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_cs_rede_technical_reserve_flag FOREIGN KEY (flag_id) REFERENCES cs_flag(id) ON UPDATE CASCADE,
  CONSTRAINT fk_cs_rede_technical_reserve_acquirer FOREIGN KEY (acquirer_id) REFERENCES cs_acquirer(id) ON UPDATE CASCADE,
  CONSTRAINT fk_cs_rede_technical_reserve_company FOREIGN KEY (company_id) REFERENCES cs_company(id) ON UPDATE CASCADE,
  CONSTRAINT fk_cs_rede_technical_reserve_establishment FOREIGN KEY (establishment_id) REFERENCES cs_establishment(id) ON UPDATE CASCADE,
  CONSTRAINT fk_cs_rede_technical_reserve_summary FOREIGN KEY (sales_summary_id) REFERENCES cs_sales_summary(id) ON UPDATE CASCADE,
  CONSTRAINT fk_cs_rede_technical_reserve_processed_file FOREIGN KEY (processed_file_id) REFERENCES cs_processed_file(id) ON UPDATE CASCADE
);
CREATE INDEX idx_cs_rede_technical_reserve_pv_rv ON cs_rede_technical_reserve(pv_number, rv_number_original);
