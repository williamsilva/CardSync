INSERT INTO cs_establishment (id, acquirer_id, type, pv_number, status, company_id, created_at, updated_at, created_by_id) VALUES
    (UUID_TO_BIN(UUID()), (SELECT id FROM cs_acquirer WHERE cnpj = '01425787000104'), 1, 7867379, 1, (SELECT id FROM cs_company WHERE cnpj = '39303847000180'),
        NOW(), NOW(),(SELECT id FROM cs_users WHERE user_name = 'suporte@cardsync.com.br')),
    (UUID_TO_BIN(UUID()), (SELECT id FROM cs_acquirer WHERE cnpj = '01425787000104'), 2, 93693702, 1, (SELECT id FROM cs_company WHERE cnpj = '39303847000180'),
         NOW(), NOW(),(SELECT id FROM cs_users WHERE user_name = 'suporte@cardsync.com.br')),
    (UUID_TO_BIN(UUID()), (SELECT id FROM cs_acquirer WHERE cnpj = '01425787000104'), 1, 7866470, 1, (SELECT id FROM cs_company WHERE cnpj = '36033801000109'),
         NOW(), NOW(),(SELECT id FROM cs_users WHERE user_name = 'suporte@cardsync.com.br')),
    (UUID_TO_BIN(UUID()), (SELECT id FROM cs_acquirer WHERE cnpj = '01425787000104'), 2, 88033759, 1, (SELECT id FROM cs_company WHERE cnpj = '36033801000109'),
         NOW(), NOW(),(SELECT id FROM cs_users WHERE user_name = 'suporte@cardsync.com.br')),
    (UUID_TO_BIN(UUID()), (SELECT id FROM cs_acquirer WHERE cnpj = '01425787000104'), 1, 74705318, 1, (SELECT id FROM cs_company WHERE cnpj = '28499334000170'),
         NOW(), NOW(),(SELECT id FROM cs_users WHERE user_name = 'suporte@cardsync.com.br')),
    (UUID_TO_BIN(UUID()), (SELECT id FROM cs_acquirer WHERE cnpj = '01425787000104'), 2, 78589126, 1, (SELECT id FROM cs_company WHERE cnpj = '28499334000170'),
         NOW(), NOW(),(SELECT id FROM cs_users WHERE user_name = 'suporte@cardsync.com.br')),
    (UUID_TO_BIN(UUID()), (SELECT id FROM cs_acquirer WHERE cnpj = '47848271000165'), 1, 1051583117, 1, (SELECT id FROM cs_company WHERE cnpj = '36033801000109'),
         NOW(), NOW(),(SELECT id FROM cs_users WHERE user_name = 'suporte@cardsync.com.br')),
    (UUID_TO_BIN(UUID()), (SELECT id FROM cs_acquirer WHERE cnpj = '47848271000165'), 2, 1018802468, 1, (SELECT id FROM cs_company WHERE cnpj = '36033801000109'),
         NOW(), NOW(),(SELECT id FROM cs_users WHERE user_name = 'suporte@cardsync.com.br')),
    (UUID_TO_BIN(UUID()), (SELECT id FROM cs_acquirer WHERE cnpj = '47848271000165'), 1, 1100125202, 1, (SELECT id FROM cs_company WHERE cnpj = '28499334000170'),
         NOW(), NOW(),(SELECT id FROM cs_users WHERE user_name = 'suporte@cardsync.com.br'));

INSERT INTO cs_permissions (id, name, description) VALUES
  (UUID_TO_BIN(UUID()), 'ESTABLISHMENT_CHANGE', 'Altera estabelecimentos'), (UUID_TO_BIN(UUID()), 'ESTABLISHMENT_CREATE', 'Cadastra estabelecimentos'),
  (UUID_TO_BIN(UUID()), 'ESTABLISHMENT_CONSULT', 'Consulta estabelecimentos'), (UUID_TO_BIN(UUID()), 'ESTABLISHMENT_DELETE', 'Excluir estabelecimentos'),
  (UUID_TO_BIN(UUID()), 'ESTABLISHMENT_ACTIVE_OR_INACTIVE', 'Ativa ou desativa estabelecimentos');

INSERT INTO cs_groups_permissions (group_id, permission_id) VALUES
  ((SELECT id FROM cs_groups WHERE name = 'ADMINISTRADOR'), (SELECT id FROM cs_permissions WHERE name = 'ESTABLISHMENT_CHANGE')),
  ((SELECT id FROM cs_groups WHERE name = 'ADMINISTRADOR'), (SELECT id FROM cs_permissions WHERE name = 'ESTABLISHMENT_CONSULT')),
  ((SELECT id FROM cs_groups WHERE name = 'ADMINISTRADOR'), (SELECT id FROM cs_permissions WHERE name = 'ESTABLISHMENT_CREATE')),
  ((SELECT id FROM cs_groups WHERE name = 'ADMINISTRADOR'), (SELECT id FROM cs_permissions WHERE name = 'ESTABLISHMENT_DELETE')),
  ((SELECT id FROM cs_groups WHERE name = 'ADMINISTRADOR'), (SELECT id FROM cs_permissions WHERE name = 'ESTABLISHMENT_ACTIVE_OR_INACTIVE'));