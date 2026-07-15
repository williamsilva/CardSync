-- Adiciona o período de vigência da conta ao domicílio bancário.
-- Para os registros existentes, a abertura assume a data de implantação do sistema (2024-07-01).
-- O encerramento é opcional: NULL indica conta sem data de encerramento definida.

ALTER TABLE cs_banking_domicile
    ADD COLUMN account_opening_date DATE NULL,
    ADD COLUMN account_closing_date DATE NULL;

UPDATE cs_banking_domicile
    SET account_opening_date = DATE('2024-07-01')
    WHERE account_opening_date IS NULL;

ALTER TABLE cs_banking_domicile
  ALTER COLUMN account_opening_date SET NOT NULL;

ALTER TABLE cs_banking_domicile
    ADD CONSTRAINT chk_banking_domicile_account_period
    CHECK (account_closing_date IS NULL OR account_closing_date >= account_opening_date);
