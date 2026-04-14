CREATE TABLE cs_establishment (
  id BINARY(16) NOT NULL,

  -- EntityBase (auditoria)
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP(6),
  created_by_id BINARY(16) NULL,
  updated_by_id BINARY(16) NULL,

  -- cs_establishment
  type INT NOT NULL DEFAULT 1,
  status INT NOT NULL DEFAULT 1,

  pv_number BIGINT NOT NULL,
  company_id BINARY(16) NULL,
  acquirer_id BINARY(16) NULL,

  PRIMARY KEY (id),

  CONSTRAINT fk_cs_establishment_created_by FOREIGN KEY (created_by_id)
    REFERENCES cs_users(id) ON DELETE SET NULL ON UPDATE CASCADE,

  CONSTRAINT fk_cs_establishment_company FOREIGN KEY (company_id)
    REFERENCES cs_company(id) ON DELETE SET NULL ON UPDATE CASCADE,

  CONSTRAINT fk_cs_establishment_acquirer FOREIGN KEY (acquirer_id)
    REFERENCES cs_acquirer(id) ON DELETE SET NULL ON UPDATE CASCADE,

  CONSTRAINT fk_cs_establishment_updated_by FOREIGN KEY (updated_by_id)
   REFERENCES cs_users(id) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB;

CREATE INDEX idx_cs_establishment_pv_number ON cs_establishment (pv_number);

ALTER TABLE cs_establishment
    ADD CONSTRAINT uk_cs_establishment_pv_company_acquirer
        UNIQUE (pv_number, company_id, acquirer_id);