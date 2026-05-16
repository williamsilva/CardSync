INSERT INTO cs_users (id, name, user_name, document,  created_at, updated_at, password_expires_at, created_by_id, password_hash) VALUES
	('97d48758','Suporte Sistema', 'suporte@cardsync.com.br', '12345678912', NOW(), NOW(), DATE_ADD(NOW(), INTERVAL 90 DAY),
	  '97d48758', '$2a$10$Le0LMZWPAhWqgkI8TbjqCOo1gCkhUplcCMZsMUS/scRl4dpgvGWAi');

INSERT INTO cs_users (id, name, user_name, document,  created_at, updated_at, password_expires_at, created_by_id, password_hash) VALUES
	('97d48756','William Silva', 'william@cardsync.com.br', '13582679799', NOW(), NOW(), DATE_ADD(NOW(), INTERVAL 90 DAY),
	  '97d48756', '$2a$10$Le0LMZWPAhWqgkI8TbjqCOo1gCkhUplcCMZsMUS/scRl4dpgvGWAi');

INSERT INTO cs_groups (id, name, description, created_at, updated_at, created_by_id) VALUES
  (UUID_TO_BIN(UUID()), 'SUPPORT', 'Suporte sistema', NOW(), NOW(),
    (SELECT id FROM cs_users WHERE user_name = 'suporte@cardsync.com.br')),
  (UUID_TO_BIN(UUID()), 'ADMINISTRADOR', 'Administrador do sistema', NOW(), NOW(),
    (SELECT id FROM cs_users WHERE user_name = 'suporte@cardsync.com.br'));

INSERT INTO cs_permissions (id, name, description) VALUES
   (UUID_TO_BIN(UUID()), 'SUPPORT', 'Suporte Sistema'),

  (UUID_TO_BIN(UUID()), 'USERS_CHANGE', 'Altera usuários'), (UUID_TO_BIN(UUID()), 'USERS_CREATE', 'Cadastra usuários'),
  (UUID_TO_BIN(UUID()), 'USERS_CONSULT', 'Consulta usuários'), (UUID_TO_BIN(UUID()), 'USERS_DELETE', 'Excluir usuários'),
  (UUID_TO_BIN(UUID()), 'USERS_ACTIVE_OR_INACTIVE', 'Ativa ou desativa usuários'),
  (UUID_TO_BIN(UUID()), 'USERS_RESEND_INVITE', 'Reenvia convite primeiro acesso'),
  (UUID_TO_BIN(UUID()), 'USERS_CHANGE_PASSWORD', 'Pode alterar propria senha'),

  (UUID_TO_BIN(UUID()), 'GROUPS_CHANGE', 'Altera grupos'), (UUID_TO_BIN(UUID()), 'GROUPS_CREATE', 'Cadastra grupos'),
  (UUID_TO_BIN(UUID()), 'GROUPS_CONSULT', 'Consulta grupos'), (UUID_TO_BIN(UUID()), 'GROUPS_DELETE', 'Excluir grupos'),

  (UUID_TO_BIN(UUID()), 'AUDIT_MAIL_CONSULT', 'Consulta E-mails enviados'),

  (UUID_TO_BIN(UUID()), 'GROUPS_MANAGEMENT_USER', 'Adiciona usuário ao grupo'),
  (UUID_TO_BIN(UUID()), 'GROUPS_MANAGEMENT_PERMISSION', 'Adiciona permissão ao grupo');

INSERT INTO cs_users_groups (user_id, group_id) VALUES
  ((SELECT id FROM cs_users WHERE user_name = 'suporte@cardsync.com.br'), (SELECT id FROM cs_groups WHERE name = 'SUPPORT')),
  ((SELECT id FROM cs_users WHERE user_name = 'william@cardsync.com.br'), (SELECT id FROM cs_groups WHERE name = 'ADMINISTRADOR'));

INSERT INTO cs_groups_permissions (group_id, permission_id) VALUES
  ((SELECT id FROM cs_groups WHERE name = 'SUPPORT'), (SELECT id FROM cs_permissions WHERE name = 'SUPPORT')),

  ((SELECT id FROM cs_groups WHERE name = 'ADMINISTRADOR'), (SELECT id FROM cs_permissions WHERE name = 'USERS_CHANGE')),
  ((SELECT id FROM cs_groups WHERE name = 'ADMINISTRADOR'), (SELECT id FROM cs_permissions WHERE name = 'USERS_CONSULT')),
  ((SELECT id FROM cs_groups WHERE name = 'ADMINISTRADOR'), (SELECT id FROM cs_permissions WHERE name = 'USERS_CREATE')),
  ((SELECT id FROM cs_groups WHERE name = 'ADMINISTRADOR'), (SELECT id FROM cs_permissions WHERE name = 'USERS_DELETE')),
  ((SELECT id FROM cs_groups WHERE name = 'ADMINISTRADOR'), (SELECT id FROM cs_permissions WHERE name = 'USERS_RESEND_INVITE')),
  ((SELECT id FROM cs_groups WHERE name = 'ADMINISTRADOR'), (SELECT id FROM cs_permissions WHERE name = 'USERS_CHANGE_PASSWORD')),
  ((SELECT id FROM cs_groups WHERE name = 'ADMINISTRADOR'), (SELECT id FROM cs_permissions WHERE name = 'USERS_ACTIVE_OR_INACTIVE')),

  ((SELECT id FROM cs_groups WHERE name = 'ADMINISTRADOR'), (SELECT id FROM cs_permissions WHERE name = 'GROUPS_CHANGE')),
  ((SELECT id FROM cs_groups WHERE name = 'ADMINISTRADOR'), (SELECT id FROM cs_permissions WHERE name = 'GROUPS_CONSULT')),
  ((SELECT id FROM cs_groups WHERE name = 'ADMINISTRADOR'), (SELECT id FROM cs_permissions WHERE name = 'GROUPS_CREATE')),
  ((SELECT id FROM cs_groups WHERE name = 'ADMINISTRADOR'), (SELECT id FROM cs_permissions WHERE name = 'GROUPS_DELETE')),

  ((SELECT id FROM cs_groups WHERE name = 'ADMINISTRADOR'), (SELECT id FROM cs_permissions WHERE name = 'GROUPS_MANAGEMENT_USER')),
  ((SELECT id FROM cs_groups WHERE name = 'ADMINISTRADOR'), (SELECT id FROM cs_permissions WHERE name = 'GROUPS_MANAGEMENT_PERMISSION')),

  ((SELECT id FROM cs_groups WHERE name = 'ADMINISTRADOR'), (SELECT id FROM cs_permissions WHERE name = 'AUDIT_MAIL_CONSULT'));
