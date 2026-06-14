-- Adiciona o período obrigatório de vigência da conta ao domicílio bancário.
-- Para os registros existentes, a abertura assume a data de criação do cadastro e
-- o encerramento recebe 9999-12-31 até que a data real seja informada.

ALTER TABLE cs_banking_domicile
  ADD COLUMN account_opening_date DATE NULL AFTER account_digit,
  ADD COLUMN account_closing_date DATE NULL AFTER account_opening_date;

UPDATE cs_banking_domicile
SET account_opening_date = COALESCE(DATE(created_at), CURRENT_DATE),
    account_closing_date = DATE('9999-12-31')
WHERE account_opening_date IS NULL
   OR account_closing_date IS NULL;

ALTER TABLE cs_banking_domicile
  MODIFY COLUMN account_opening_date DATE NOT NULL,
  MODIFY COLUMN account_closing_date DATE NOT NULL;

ALTER TABLE cs_banking_domicile
  ADD CONSTRAINT chk_banking_domicile_account_period
  CHECK (account_closing_date >= account_opening_date);
