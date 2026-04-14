CREATE TABLE cs_contracts (
    id BINARY(16) NOT NULL,
    status BIGINT NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE,
    description VARCHAR(150) NOT NULL,
    company_id BINARY(16) NULL,
    acquirer_id BINARY(16) NOT NULL,
    establishment_id BINARY(16) NULL,

    PRIMARY KEY (id),

    FOREIGN KEY (company_id) REFERENCES cs_company(id),
    FOREIGN KEY (acquirer_id) REFERENCES cs_acquirer(id),
    FOREIGN KEY (establishment_id) REFERENCES cs_establishment(id)
);

CREATE TABLE cs_contract_flags (
    id BINARY(16) NOT NULL,
    flag_id BINARY(16) NULL,
    contract_id BINARY(16) NULL,

    PRIMARY KEY (id),

    CONSTRAINT fk_cs_contract_flags_flag FOREIGN KEY (flag_id)
    REFERENCES cs_flag(id) ON DELETE SET NULL ON UPDATE CASCADE,

    CONSTRAINT fk_cs_contract_flags_contracts FOREIGN KEY (contract_id)
    REFERENCES cs_contracts(id) ON DELETE SET NULL ON UPDATE CASCADE
);

CREATE TABLE cs_contract_rates (
    id BINARY(16) NOT NULL,
    modality BIGINT NOT NULL,
    rate DECIMAL(18,8) NOT NULL,
    rate_ecommerce DECIMAL(18,8) NOT NULL,
    payment_term_days BIGINT NOT NULL,
    payment_term_days_ecommerce BIGINT NOT NULL,
    contract_flag_id BINARY(16) NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (contract_flag_id) REFERENCES cs_contract_flags(id)
);

INSERT INTO cs_permissions (id, name, description) VALUES
  (UUID_TO_BIN(UUID()), 'CONTRACTS_CHANGE', 'Altera contratos'), (UUID_TO_BIN(UUID()), 'CONTRACTS_CREATE', 'Cadastra contratos'),
  (UUID_TO_BIN(UUID()), 'CONTRACTS_CONSULT', 'Consulta contratos'), (UUID_TO_BIN(UUID()), 'CONTRACTS_DELETE', 'Excluir contratos'),
  (UUID_TO_BIN(UUID()), 'CONTRACTS_ACTIVE_OR_INACTIVE', 'Ativa ou desativa contratos');

INSERT INTO cs_groups_permissions (group_id, permission_id) VALUES
  ((SELECT id FROM cs_groups WHERE name = 'ADMINISTRADOR'), (SELECT id FROM cs_permissions WHERE name = 'CONTRACTS_CHANGE')),
  ((SELECT id FROM cs_groups WHERE name = 'ADMINISTRADOR'), (SELECT id FROM cs_permissions WHERE name = 'CONTRACTS_CONSULT')),
  ((SELECT id FROM cs_groups WHERE name = 'ADMINISTRADOR'), (SELECT id FROM cs_permissions WHERE name = 'CONTRACTS_CREATE')),
  ((SELECT id FROM cs_groups WHERE name = 'ADMINISTRADOR'), (SELECT id FROM cs_permissions WHERE name = 'CONTRACTS_DELETE')),
  ((SELECT id FROM cs_groups WHERE name = 'ADMINISTRADOR'), (SELECT id FROM cs_permissions WHERE name = 'CONTRACTS_ACTIVE_OR_INACTIVE'));