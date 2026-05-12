ALTER TABLE cs_transaction_erp
  ADD COLUMN banking_domicile_id BINARY(16) NULL AFTER company_id,
  ADD CONSTRAINT fk_cs_transaction_erp_banking_domicile
    FOREIGN KEY (banking_domicile_id) REFERENCES cs_banking_domicile(id) ON UPDATE CASCADE;

CREATE INDEX idx_cs_transaction_erp_banking_domicile ON cs_transaction_erp(banking_domicile_id);
