-- Permite identificar o domicílio do arquivo CNAB mesmo quando não existem lançamentos.
ALTER TABLE cs_processed_file
  ADD COLUMN banking_domicile_id UUID NULL;

CREATE INDEX idx_processed_file_banking_domicile
  ON cs_processed_file (banking_domicile_id);

ALTER TABLE cs_processed_file
  ADD CONSTRAINT fk_processed_file_banking_domicile
    FOREIGN KEY (banking_domicile_id) REFERENCES cs_banking_domicile (id);
