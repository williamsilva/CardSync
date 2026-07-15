CREATE TABLE cs_bank (
  id UUID NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NULL DEFAULT CURRENT_TIMESTAMP(6),
  created_by_id UUID NULL,
  updated_by_id UUID NULL,
  code VARCHAR(10) NOT NULL,
  name VARCHAR(100) NOT NULL,
  ispb VARCHAR(20) NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  PRIMARY KEY (id),
  CONSTRAINT uk_cs_bank_code UNIQUE (code)
);

CREATE TABLE cs_banking_domicile (
  id UUID NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NULL DEFAULT CURRENT_TIMESTAMP(6),
  created_by_id UUID NULL,
  updated_by_id UUID NULL,
  agency INT NULL,
  agency_digit VARCHAR(5) NULL,
  current_account INT NULL,
  account_digit INT NULL,
  holder_document VARCHAR(20) NULL,
  holder_name VARCHAR(120) NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  bank_id UUID NULL,
  company_id UUID NULL,
  establishment_id UUID NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_cs_banking_domicile_bank FOREIGN KEY (bank_id) REFERENCES cs_bank(id) ON UPDATE CASCADE,
  CONSTRAINT fk_cs_banking_domicile_company FOREIGN KEY (company_id) REFERENCES cs_company(id) ON UPDATE CASCADE,
  CONSTRAINT fk_cs_banking_domicile_establishment FOREIGN KEY (establishment_id) REFERENCES cs_establishment(id) ON UPDATE CASCADE
);
CREATE INDEX idx_cs_banking_domicile_lookup ON cs_banking_domicile(agency, current_account);
CREATE INDEX idx_cs_banking_domicile_company ON cs_banking_domicile(company_id);
