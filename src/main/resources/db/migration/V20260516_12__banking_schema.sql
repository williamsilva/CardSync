CREATE TABLE cs_bank (
  id BINARY(16) NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  created_by_id BINARY(16) NULL,
  updated_by_id BINARY(16) NULL,
  code VARCHAR(10) NOT NULL,
  name VARCHAR(100) NOT NULL,
  ispb VARCHAR(20) NULL,
  active BIT NOT NULL DEFAULT 1,
  PRIMARY KEY (id),
  CONSTRAINT uk_cs_bank_code UNIQUE (code),
  CONSTRAINT fk_cs_bank_created_by FOREIGN KEY (created_by_id) REFERENCES cs_users(id) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT fk_cs_bank_updated_by FOREIGN KEY (updated_by_id) REFERENCES cs_users(id) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB;

CREATE TABLE cs_banking_domicile (
  id BINARY(16) NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  created_by_id BINARY(16) NULL,
  updated_by_id BINARY(16) NULL,
  agency INT NULL,
  agency_digit VARCHAR(5) NULL,
  current_account INT NULL,
  account_digit INT NULL,
  holder_document VARCHAR(20) NULL,
  holder_name VARCHAR(120) NULL,
  active BIT NOT NULL DEFAULT 1,
  bank_id BINARY(16) NULL,
  company_id BINARY(16) NULL,
  establishment_id BINARY(16) NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_cs_banking_domicile_bank FOREIGN KEY (bank_id) REFERENCES cs_bank(id) ON UPDATE CASCADE,
  CONSTRAINT fk_cs_banking_domicile_company FOREIGN KEY (company_id) REFERENCES cs_company(id) ON UPDATE CASCADE,
  CONSTRAINT fk_cs_banking_domicile_establishment FOREIGN KEY (establishment_id) REFERENCES cs_establishment(id) ON UPDATE CASCADE,
  CONSTRAINT fk_cs_banking_domicile_created_by FOREIGN KEY (created_by_id) REFERENCES cs_users(id) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT fk_cs_banking_domicile_updated_by FOREIGN KEY (updated_by_id) REFERENCES cs_users(id) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB;
CREATE INDEX idx_cs_banking_domicile_lookup ON cs_banking_domicile(agency, current_account);
CREATE INDEX idx_cs_banking_domicile_company ON cs_banking_domicile(company_id);
