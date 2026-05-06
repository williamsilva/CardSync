ALTER TABLE cs_contract_rates
  ADD COLUMN installment_min INT NULL AFTER payment_term_days,
  ADD COLUMN installment_max INT NULL AFTER installment_min;

DROP INDEX uq_cs_contract_rates_contract_flag_modality ON cs_contract_rates;

CREATE UNIQUE INDEX uq_cs_contract_rates_contract_flag_modality_installment_range
  ON cs_contract_rates (contract_flag_id, modality, installment_min, installment_max);

CREATE INDEX idx_cs_contract_rates_installment_range
  ON cs_contract_rates (installment_min, installment_max);

ALTER TABLE cs_transaction_erp
  ADD COLUMN source_company_cnpj VARCHAR(32) NULL AFTER authorization,
  ADD COLUMN source_company_name VARCHAR(255) NULL AFTER source_company_cnpj,
  ADD COLUMN source_establishment_pv_number INT NULL AFTER source_company_name,
  ADD COLUMN source_establishment_name VARCHAR(255) NULL AFTER source_establishment_pv_number;

CREATE INDEX idx_cs_transaction_erp_source_company_cnpj ON cs_transaction_erp(source_company_cnpj);
CREATE INDEX idx_cs_transaction_erp_source_establishment_pv ON cs_transaction_erp(source_establishment_pv_number);
