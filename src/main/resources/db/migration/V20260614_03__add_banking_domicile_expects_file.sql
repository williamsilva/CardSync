ALTER TABLE cs_banking_domicile
    ADD COLUMN expects_file BOOLEAN NULL AFTER account_closing_date;

UPDATE cs_banking_domicile
SET expects_file = TRUE
WHERE expects_file IS NULL;

ALTER TABLE cs_banking_domicile
    MODIFY COLUMN expects_file BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE cs_banking_domicile DROP FOREIGN KEY `fk_cs_banking_domicile_establishment`;
ALTER TABLE cs_banking_domicile DROP COLUMN `establishment_id`,  DROP COLUMN `holder_name`,
DROP COLUMN `holder_document`, DROP INDEX `fk_cs_banking_domicile_establishment` ;

ALTER TABLE cs_banking_domicile DROP COLUMN `active`;