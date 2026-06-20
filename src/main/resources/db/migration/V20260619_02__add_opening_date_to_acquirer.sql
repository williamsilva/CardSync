ALTER TABLE cs_acquirer ADD COLUMN opening_date DATE NULL AFTER file_identifier;

ALTER TABLE cs_acquirer ADD COLUMN closing_date DATE NULL AFTER opening_date;

ALTER TABLE cs_banking_domicile MODIFY COLUMN account_closing_date DATE NULL;

UPDATE cs_banking_domicile SET account_closing_date = NULL WHERE status = 1;
