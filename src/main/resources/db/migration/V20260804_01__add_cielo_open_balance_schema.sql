-- Saldo em aberto da Cielo (CIELO09) — snapshot mensal de URs ainda não pagas. Tabela isolada e
-- própria (não reusa cs_credit_order): a mesma "Chave UR" de uma linha aqui vai reaparecer, mais
-- tarde, num CIELO04 real quando a UR for efetivamente liquidada — se fosse a mesma tabela do
-- CreditOrderEntity, o BankReconciliationService poderia casar um lançamento bancário real contra
-- a ordem "prevista" errada. O próprio manual da Cielo diz que este arquivo não deve ser usado
-- para fins de conciliação transacional (ver ProcessCielo09Service).
CREATE TABLE cs_open_balance (
  id UUID NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NULL DEFAULT CURRENT_TIMESTAMP(6),
  created_by_id UUID NULL,
  updated_by_id UUID NULL,
  pv_number INT NULL,
  rv_number INT NULL,
  line_number INT NULL,
  number_of_releases INT NULL,
  settlement_type INT NULL,
  payment_status INT NULL,
  launch_type VARCHAR(10) NULL,
  open_balance_indicator VARCHAR(5) NULL,
  payment_date DATE NULL,
  original_due_date DATE NULL,
  gross_value DECIMAL(18,8) NULL,
  liquid_value DECIMAL(18,8) NULL,
  flag_id UUID NULL,
  company_id UUID NULL,
  acquirer_id UUID NULL,
  processed_file_id UUID NULL,
  establishment_id UUID NULL,
  banking_domicile_id UUID NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_cs_open_balance_flag FOREIGN KEY (flag_id) REFERENCES cs_flag(id) ON UPDATE CASCADE,
  CONSTRAINT fk_cs_open_balance_company FOREIGN KEY (company_id) REFERENCES cs_company(id) ON UPDATE CASCADE,
  CONSTRAINT fk_cs_open_balance_acquirer FOREIGN KEY (acquirer_id) REFERENCES cs_acquirer(id) ON UPDATE CASCADE,
  CONSTRAINT fk_cs_open_balance_processed_file FOREIGN KEY (processed_file_id) REFERENCES cs_processed_file(id) ON UPDATE CASCADE,
  CONSTRAINT fk_cs_open_balance_establishment FOREIGN KEY (establishment_id) REFERENCES cs_establishment(id) ON UPDATE CASCADE,
  CONSTRAINT fk_cs_open_balance_banking_domicile FOREIGN KEY (banking_domicile_id) REFERENCES cs_banking_domicile(id) ON UPDATE CASCADE
);

CREATE INDEX idx_cs_open_balance_pv_payment ON cs_open_balance(pv_number, payment_date);
