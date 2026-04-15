CREATE TABLE cs_flag (
  id BINARY(16) NOT NULL,
  status INT NOT NULL DEFAULT 1,
  erp_code BIGINT NOT NULL,
  name VARCHAR(20) NOT NULL UNIQUE,
  text_color VARCHAR(20) NULL,

  PRIMARY KEY (id)
) ENGINE=InnoDB;

CREATE INDEX idx_cs_flag_name ON cs_flag (name);

CREATE TABLE cs_flag_acquirer (
  id BINARY(16) NOT NULL,
  acquirer_id BINARY(16) NOT NULL,
  flag_id BINARY(16) NOT NULL,
  acquirer_code VARCHAR(2) NOT NULL,
  FOREIGN KEY (flag_id) REFERENCES cs_flag(id),
  FOREIGN KEY (acquirer_id) REFERENCES cs_acquirer(id),
   UNIQUE (flag_id, acquirer_id)
)engine=InnoDB DEFAULT CHARSET=UTF8MB4;

CREATE TABLE cs_flag_company (
  id BINARY(16) NOT NULL,
  company_id BINARY(16) NOT NULL,
  flag_id BINARY(16) NOT NULL,
  FOREIGN KEY (flag_id) REFERENCES cs_flag(id),
  FOREIGN KEY (company_id) REFERENCES cs_company(id),
   UNIQUE (flag_id, company_id)
)engine=InnoDB DEFAULT CHARSET=UTF8MB4;

INSERT INTO cs_permissions (id, name, description) VALUES
  (UUID_TO_BIN(UUID()), 'FLAGS_CHANGE', 'Altera bandeiras'), (UUID_TO_BIN(UUID()), 'FLAGS_CREATE', 'Cadastra bandeiras'),
  (UUID_TO_BIN(UUID()), 'FLAGS_CONSULT', 'Consulta bandeiras'), (UUID_TO_BIN(UUID()), 'FLAGS_DELETE', 'Excluir bandeiras'),
  (UUID_TO_BIN(UUID()), 'PERM_FLAGS_MANAGE_RELATIONS', 'Relaciona bandeiras a adquirentes e empresas'),
  (UUID_TO_BIN(UUID()), 'FLAGS_ACTIVE_OR_INACTIVE', 'Ativa ou desativa bandeiras');

INSERT INTO cs_groups_permissions (group_id, permission_id) VALUES
  ((SELECT id FROM cs_groups WHERE name = 'ADMINISTRADOR'), (SELECT id FROM cs_permissions WHERE name = 'FLAGS_CHANGE')),
  ((SELECT id FROM cs_groups WHERE name = 'ADMINISTRADOR'), (SELECT id FROM cs_permissions WHERE name = 'FLAGS_CONSULT')),
  ((SELECT id FROM cs_groups WHERE name = 'ADMINISTRADOR'), (SELECT id FROM cs_permissions WHERE name = 'FLAGS_CREATE')),
  ((SELECT id FROM cs_groups WHERE name = 'ADMINISTRADOR'), (SELECT id FROM cs_permissions WHERE name = 'FLAGS_DELETE')),
  ((SELECT id FROM cs_groups WHERE name = 'ADMINISTRADOR'), (SELECT id FROM cs_permissions WHERE name = 'FLAGS_ACTIVE_OR_INACTIVE'));