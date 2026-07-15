CREATE TABLE cs_company (
  id UUID NOT NULL,

  -- EntityBase (auditoria)
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NULL DEFAULT NULL,
  created_by_id UUID NULL,
  updated_by_id UUID NULL,

  -- cs_company
  type INT NOT NULL DEFAULT 1,
  status INT NOT NULL DEFAULT 1,

  fantasy_name VARCHAR(50) NOT NULL,
  social_reason VARCHAR(50) NOT NULL,
  cnpj VARCHAR(20) NOT NULL UNIQUE,

  PRIMARY KEY (id)


);

CREATE INDEX idx_cs_company_fantasy_name ON cs_company (fantasy_name);
CREATE INDEX idx_cs_company_social_reason ON cs_company (social_reason);

CREATE TABLE cs_acquirer (
  id UUID NOT NULL,

  -- EntityBase (auditoria)
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NULL DEFAULT NULL,
  created_by_id UUID NULL,
  updated_by_id UUID NULL,

  -- cs_acquirer
  type INT NOT NULL DEFAULT 1,
  status INT NOT NULL DEFAULT 1,

  fantasy_name VARCHAR(50) NOT NULL,
  social_reason VARCHAR(50) NOT NULL,
  cnpj VARCHAR(20) NOT NULL UNIQUE,
  file_identifier VARCHAR(30) NOT NULL UNIQUE,

  PRIMARY KEY (id)


);

CREATE INDEX idx_cs_acquirer_fantasy_name ON cs_acquirer (fantasy_name);
CREATE INDEX idx_cs_acquirer_social_reason ON cs_acquirer (social_reason);
