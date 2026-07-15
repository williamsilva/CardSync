ALTER TABLE cs_banking_domicile
    ADD COLUMN expects_file BOOLEAN NULL;

UPDATE cs_banking_domicile
    SET expects_file = TRUE
    WHERE expects_file IS NULL;

ALTER TABLE cs_banking_domicile
    ALTER COLUMN expects_file SET NOT NULL,
    ALTER COLUMN expects_file SET DEFAULT TRUE;

ALTER TABLE cs_banking_domicile DROP CONSTRAINT fk_cs_banking_domicile_establishment;

ALTER TABLE cs_banking_domicile
    DROP COLUMN establishment_id,
    DROP COLUMN holder_name,
    DROP COLUMN holder_document;

ALTER TABLE cs_banking_domicile DROP COLUMN active;

ALTER TABLE cs_banking_domicile
    ADD COLUMN status     INT          NOT NULL DEFAULT 1,
    ADD COLUMN status_date TIMESTAMP(6) NULL;

UPDATE cs_banking_domicile SET status = 2, status_date = '2025-05-10 00:00:00', account_closing_date = '2025-05-10'
    WHERE current_account = 13005467 AND account_digit = '0';
UPDATE cs_banking_domicile SET status = 2, status_date = '2024-07-01 00:00:00', account_closing_date = '2024-07-01'
    WHERE current_account = 43118    AND account_digit = '1';
UPDATE cs_banking_domicile SET status = 2, status_date = '2024-07-01 00:00:00', account_closing_date = '2024-07-01'
    WHERE current_account = 40339    AND account_digit = '6';

INSERT INTO cs_banking_domicile(id, agency, current_account, account_digit, bank_id, company_id, created_at, updated_at, created_by_id, account_opening_date) VALUES
    (gen_random_uuid(),226, 81351, 8, (SELECT id FROM cs_bank WHERE code = '748'),
	(SELECT id FROM cs_company WHERE cnpj = '39303847000180'), NOW(), NOW(),
	NULL, '2026-03-29');
