ALTER TABLE cs_no_file_day
  ADD COLUMN banking_domicile_id BINARY(16) NULL AFTER file_group,
  ADD COLUMN acquirer_file_type VARCHAR(20) NULL AFTER acquirer_id;

-- Preserva automaticamente vínculos antigos quando o banco possui somente um domicílio.
UPDATE cs_no_file_day nfd
JOIN (
  SELECT bank_id, MIN(id) AS banking_domicile_id
  FROM cs_banking_domicile
  GROUP BY bank_id
  HAVING COUNT(*) = 1
) domicile ON domicile.bank_id = nfd.bank_id
SET nfd.banking_domicile_id = domicile.banking_domicile_id
WHERE nfd.file_group = 'BANK';

ALTER TABLE cs_no_file_day
  ADD CONSTRAINT fk_cs_no_file_day_banking_domicile
    FOREIGN KEY (banking_domicile_id) REFERENCES cs_banking_domicile (id);

ALTER TABLE cs_no_file_day
  DROP FOREIGN KEY fk_cs_no_file_day_bank,
  DROP COLUMN bank_id;

CREATE INDEX idx_cs_no_file_day_domicile_date
  ON cs_no_file_day (banking_domicile_id, no_file_date);

CREATE INDEX idx_cs_no_file_day_acquirer_type_date
  ON cs_no_file_day (acquirer_id, acquirer_file_type, no_file_date);
