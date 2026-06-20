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

ALTER TABLE cs_banking_domicile
    ADD COLUMN status     INT          NOT NULL DEFAULT 1 AFTER expects_file,
    ADD COLUMN status_date DATETIME(6) NULL      AFTER status;

UPDATE cs_banking_domicile SET status = 2, status_date = '2025-05-10 00:00:00', account_closing_date = '2025-05-10'
    WHERE current_account = 13005467 AND account_digit = '0';
UPDATE cs_banking_domicile SET status = 2, status_date = '2024-07-01 00:00:00', account_closing_date = '2024-07-01'
    WHERE current_account = 43118    AND account_digit = '1';
UPDATE cs_banking_domicile SET status = 2, status_date = '2024-07-01 00:00:00', account_closing_date = '2024-07-01'
    WHERE current_account = 40339    AND account_digit = '6';