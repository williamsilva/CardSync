ALTER TABLE cs_no_file_day
  ADD COLUMN banking_domicile_id UUID NULL,
  ADD COLUMN acquirer_file_type VARCHAR(20) NULL;

-- Preserva automaticamente vínculos antigos quando o banco possui somente um domicílio.
UPDATE cs_no_file_day nfd
SET banking_domicile_id = domicile.banking_domicile_id
FROM (
  -- Postgres não tem MIN()/MAX() nativo para UUID; como HAVING COUNT(*)=1 garante uma
  -- única linha por grupo, o cast texto->uuid só serve para satisfazer o agregado.
  SELECT bank_id, MIN(id::text)::uuid AS banking_domicile_id
  FROM cs_banking_domicile
  GROUP BY bank_id
  HAVING COUNT(*) = 1
) domicile
WHERE domicile.bank_id = nfd.bank_id
  AND nfd.file_group = 'BANK';

ALTER TABLE cs_no_file_day
  ADD CONSTRAINT fk_cs_no_file_day_banking_domicile
    FOREIGN KEY (banking_domicile_id) REFERENCES cs_banking_domicile (id);

ALTER TABLE cs_no_file_day
  DROP CONSTRAINT fk_cs_no_file_day_bank,
  DROP COLUMN bank_id;

CREATE INDEX idx_cs_no_file_day_domicile_date
  ON cs_no_file_day (banking_domicile_id, no_file_date);

CREATE INDEX idx_cs_no_file_day_acquirer_type_date
  ON cs_no_file_day (acquirer_id, acquirer_file_type, no_file_date);
