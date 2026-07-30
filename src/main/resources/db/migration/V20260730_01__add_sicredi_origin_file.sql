-- O banco Sicredi (cs_bank.code = '748') e o domicílio bancário da conta 226/81351-8 já
-- existem (V20260516_19__banking_seed.sql / V20260614_03__add_banking_domicile_expects_file.sql).
-- Falta apenas a origem de arquivo usada por Cnab240FileProcessor para registrar o
-- ProcessedFileEntity dos extratos CNAB240 (Segmento E) importados do Sicredi.
INSERT INTO cs_origin_file (id, created_at, updated_at, code, name, description)
SELECT gen_random_uuid(), CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), 'SICREDI', 'Sicredi', 'Arquivos bancários Sicredi'
WHERE NOT EXISTS (SELECT 1 FROM cs_origin_file WHERE code = 'SICREDI');
