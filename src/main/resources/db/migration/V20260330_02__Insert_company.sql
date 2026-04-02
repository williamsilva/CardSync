
INSERT INTO cs_company(id, status, type, fantasy_name, social_reason, cnpj, created_at, updated_at, created_by_id) VALUES
    (UUID_TO_BIN(UUID()), 1, 1, 'Acquamania Multiplo Lazer S.A', 'Acquamania', '39303847000180', NOW(), NOW(),
        (SELECT id FROM cs_users WHERE user_name = 'suporte@cardsync.com.br')),
    (UUID_TO_BIN(UUID()), 1, 1, 'Clam Qualidade de Vida', 'Clam Qualidade', '36033801000109', NOW(), NOW(),
        (SELECT id FROM cs_users WHERE user_name = 'suporte@cardsync.com.br')),
    (UUID_TO_BIN(UUID()), 1, 1, 'Mac Serviços e Convêniencia LTDA', 'Mac Serviços', '28499334000170', NOW(), NOW(),
        (SELECT id FROM cs_users WHERE user_name = 'suporte@cardsync.com.br'));

INSERT INTO cs_acquirer(id, status, cnpj, fantasy_name, social_reason, file_identifier, created_at, updated_at, created_by_id) VALUES
    (UUID_TO_BIN(UUID()), 1, '01425787000104', 'Rede S/A', 'Rede', 'Rede', NOW(), NOW(),
     (SELECT id FROM cs_users WHERE user_name = 'suporte@cardsync.com.br')),
    (UUID_TO_BIN(UUID()), 1, '47848271000165', 'Cielo S/A', 'Cielo', 'Cielo', NOW(), NOW(),
     (SELECT id FROM cs_users WHERE user_name = 'suporte@cardsync.com.br')),
    (UUID_TO_BIN(UUID()), 2, '22222222222222', 'SafraPay', 'SafraPay', 'SafraPay', NOW(), NOW(),
     (SELECT id FROM cs_users WHERE user_name = 'suporte@cardsync.com.br')),
    (UUID_TO_BIN(UUID()), 1, '11111111111111', 'Outra', 'Outra Adquirente', 'Outra', NOW(), NOW(),
     (SELECT id FROM cs_users WHERE user_name = 'suporte@cardsync.com.br'));

INSERT INTO cs_permissions (id, name, description) VALUES
  (UUID_TO_BIN(UUID()), 'COMPANIES_CHANGE', 'Altera empresas'), (UUID_TO_BIN(UUID()), 'COMPANIES_CREATE', 'Cadastra empresas'),
  (UUID_TO_BIN(UUID()), 'COMPANIES_CONSULT', 'Consulta empresas'), (UUID_TO_BIN(UUID()), 'COMPANIES_DELETE', 'Excluir empresas'),
  (UUID_TO_BIN(UUID()), 'COMPANIES_ACTIVE_OR_INACTIVE', 'Ativa ou desativa empresas'),

  (UUID_TO_BIN(UUID()), 'ACQUIRER_CHANGE', 'Altera empresas'), (UUID_TO_BIN(UUID()), 'ACQUIRER_CREATE', 'Cadastra empresas'),
  (UUID_TO_BIN(UUID()), 'ACQUIRER_CONSULT', 'Consulta empresas'), (UUID_TO_BIN(UUID()), 'ACQUIRER_DELETE', 'Excluir empresas'),
  (UUID_TO_BIN(UUID()), 'ACQUIRER_ACTIVE_OR_INACTIVE', 'Ativa ou desativa empresas');

INSERT INTO cs_groups_permissions (group_id, permission_id) VALUES
  ((SELECT id FROM cs_groups WHERE name = 'ADMINISTRADOR'), (SELECT id FROM cs_permissions WHERE name = 'COMPANIES_CHANGE')),
  ((SELECT id FROM cs_groups WHERE name = 'ADMINISTRADOR'), (SELECT id FROM cs_permissions WHERE name = 'COMPANIES_CONSULT')),
  ((SELECT id FROM cs_groups WHERE name = 'ADMINISTRADOR'), (SELECT id FROM cs_permissions WHERE name = 'COMPANIES_CREATE')),
  ((SELECT id FROM cs_groups WHERE name = 'ADMINISTRADOR'), (SELECT id FROM cs_permissions WHERE name = 'COMPANIES_DELETE')),
  ((SELECT id FROM cs_groups WHERE name = 'ADMINISTRADOR'), (SELECT id FROM cs_permissions WHERE name = 'COMPANIES_ACTIVE_OR_INACTIVE')),

  ((SELECT id FROM cs_groups WHERE name = 'ADMINISTRADOR'), (SELECT id FROM cs_permissions WHERE name = 'ACQUIRER_CHANGE')),
  ((SELECT id FROM cs_groups WHERE name = 'ADMINISTRADOR'), (SELECT id FROM cs_permissions WHERE name = 'ACQUIRER_CONSULT')),
  ((SELECT id FROM cs_groups WHERE name = 'ADMINISTRADOR'), (SELECT id FROM cs_permissions WHERE name = 'ACQUIRER_CREATE')),
  ((SELECT id FROM cs_groups WHERE name = 'ADMINISTRADOR'), (SELECT id FROM cs_permissions WHERE name = 'ACQUIRER_DELETE')),
  ((SELECT id FROM cs_groups WHERE name = 'ADMINISTRADOR'), (SELECT id FROM cs_permissions WHERE name = 'ACQUIRER_ACTIVE_OR_INACTIVE'));