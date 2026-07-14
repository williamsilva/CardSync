ALTER TABLE cs_transaction_erp
  ADD COLUMN missing_contract_at_sale TINYINT(1) NOT NULL DEFAULT 0 AFTER source_establishment_pv_number;

CREATE TABLE cs_contract_audit (
  id BINARY(16) NOT NULL,
  status INT NULL,
  capture INT NULL,
  modality INT NULL,
  nsu BIGINT NULL,
  authorization VARCHAR(255) NULL,
  gross_value DECIMAL(19,2) NULL,
  liquid_value DECIMAL(19,2) NULL,
  rate_acquirer DECIMAL(19,8) NULL,
  rate_contract DECIMAL(19,8) NULL,
  discount_value DECIMAL(19,8) NULL,
  expected_discount_value DECIMAL(19,8) NULL,
  difference_value DECIMAL(19,8) NULL,
  flag_id BINARY(16) NULL,
  acquirer_id BINARY(16) NULL,
  contract_id BINARY(16) NULL,
  company_id BINARY(16) NULL,
  establishment_id BINARY(16) NULL,
  transaction_acq_id BINARY(16) NULL,
  transaction_erp_id BINARY(16) NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP(6),
  created_by_id BINARY(16) NULL,
  updated_by_id BINARY(16) NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_cs_contract_audit_flag FOREIGN KEY (flag_id) REFERENCES cs_flag(id) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT fk_cs_contract_audit_acquirer FOREIGN KEY (acquirer_id) REFERENCES cs_acquirer(id) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT fk_cs_contract_audit_contract FOREIGN KEY (contract_id) REFERENCES cs_contracts(id) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT fk_cs_contract_audit_company FOREIGN KEY (company_id) REFERENCES cs_company(id) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT fk_cs_contract_audit_establishment FOREIGN KEY (establishment_id) REFERENCES cs_establishment(id) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT fk_cs_contract_audit_transaction_acq FOREIGN KEY (transaction_acq_id) REFERENCES cs_transaction_acq(id) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT fk_cs_contract_audit_transaction_erp FOREIGN KEY (transaction_erp_id) REFERENCES cs_transaction_erp(id) ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE UNIQUE INDEX uq_cs_contract_audit_transaction_acq ON cs_contract_audit(transaction_acq_id);
CREATE INDEX idx_cs_contract_audit_transaction_erp ON cs_contract_audit(transaction_erp_id);
CREATE INDEX idx_cs_contract_audit_status ON cs_contract_audit(status);
CREATE INDEX idx_cs_contract_audit_company_acquirer ON cs_contract_audit(company_id, acquirer_id);
CREATE INDEX idx_cs_contract_audit_contract ON cs_contract_audit(contract_id);
