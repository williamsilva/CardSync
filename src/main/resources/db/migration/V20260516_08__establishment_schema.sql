CREATE TABLE cs_establishment (
  id UUID NOT NULL,

  -- EntityBase (auditoria)
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NULL DEFAULT NULL,
  created_by_id UUID NULL,
  updated_by_id UUID NULL,

  -- cs_establishment
  type INT NOT NULL DEFAULT 1,
  status INT NOT NULL DEFAULT 1,

  pv_number BIGINT NOT NULL,
  company_id UUID NULL,
  acquirer_id UUID NULL,

  PRIMARY KEY (id),


  CONSTRAINT fk_cs_establishment_company FOREIGN KEY (company_id)
    REFERENCES cs_company(id) ON DELETE SET NULL ON UPDATE CASCADE,

  CONSTRAINT fk_cs_establishment_acquirer FOREIGN KEY (acquirer_id)
    REFERENCES cs_acquirer(id) ON DELETE SET NULL ON UPDATE CASCADE

);

CREATE INDEX idx_cs_establishment_pv_number ON cs_establishment (pv_number);

ALTER TABLE cs_establishment
    ADD CONSTRAINT uk_cs_establishment_pv_company_acquirer
        UNIQUE (pv_number, company_id, acquirer_id);

CREATE TABLE cs_acquirer_establishment (
  id UUID NOT NULL,
  acquirer_id UUID NOT NULL,
  establishment_id UUID NOT NULL,
  FOREIGN KEY (establishment_id) REFERENCES cs_establishment(id),
  FOREIGN KEY (acquirer_id) REFERENCES cs_acquirer(id),
   UNIQUE (establishment_id, acquirer_id)
);

CREATE TABLE cs_acquirer_company (
  id UUID NOT NULL,
  company_id UUID NOT NULL,
  acquirer_id UUID NOT NULL,
  FOREIGN KEY (acquirer_id) REFERENCES cs_acquirer(id),
  FOREIGN KEY (company_id) REFERENCES cs_company(id),
   UNIQUE (acquirer_id, company_id)
);
