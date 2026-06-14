ALTER TABLE cs_banking_domicile
    ADD COLUMN expects_file BOOLEAN NULL AFTER account_closing_date;

UPDATE cs_banking_domicile
SET expects_file = TRUE
WHERE expects_file IS NULL;

ALTER TABLE cs_banking_domicile
    MODIFY COLUMN expects_file BOOLEAN NOT NULL DEFAULT TRUE;
