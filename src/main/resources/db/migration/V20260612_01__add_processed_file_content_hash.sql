ALTER TABLE cs_processed_file ADD COLUMN content_hash VARCHAR(64) NULL AFTER file_name;

CREATE UNIQUE INDEX uq_cs_processed_file_content_hash ON cs_processed_file (content_hash);

INSERT INTO cs_permissions (id, name, description) VALUES
  (UUID_TO_BIN(UUID()), 'ANTICIPATION_CONSULT', 'Lista transações Antecipadas'),
  (UUID_TO_BIN(UUID()), 'CREDIT_ORDER_CONSULT', 'Lista Ordens de pagamento'),
  (UUID_TO_BIN(UUID()), 'BANK_STATEMENT_CONSULT', 'Lista lançamentos bancários'),
  (UUID_TO_BIN(UUID()), 'SALES_SUMMARY_CONSULT', 'Lista Resumo de vendas'),

  (UUID_TO_BIN(UUID()), 'HOLIDAYS_CONSULT', 'Lista Feriados'),
  (UUID_TO_BIN(UUID()), 'HOLIDAYS_CREATE', 'Cadastra Feriados'),
  (UUID_TO_BIN(UUID()), 'HOLIDAYS_CHANGE', 'Altera Feriados'),
  (UUID_TO_BIN(UUID()), 'HOLIDAYS_ACTIVE_OR_INACTIVE', 'Ativa ou Inativa Feriados'),

  (UUID_TO_BIN(UUID()), 'BANKING_DOMICILE_CONSULT', 'Lista Domicilios Bancarios'),
  (UUID_TO_BIN(UUID()), 'BANKING_DOMICILE_CREATE', 'Cadastra Domicilios Bancarios'),
  (UUID_TO_BIN(UUID()), 'BANKING_DOMICILE_CHANGE', 'Altera Domicilios Bancarios'),
  (UUID_TO_BIN(UUID()), 'BANKING_DOMICILE_ACTIVE_OR_INACTIVE', 'Ativa ou Inativa Domicilios Bancarios'),

  (UUID_TO_BIN(UUID()), 'NO_FILE_DAY_CONSULT', 'Lista dias sem Arquivos'),
  (UUID_TO_BIN(UUID()), 'NO_FILE_DAY_CREATE', 'Cadastra dias sem Arquivos'),
  (UUID_TO_BIN(UUID()), 'NO_FILE_DAY_CHANGE', 'Altera dias sem Arquivos'),
  (UUID_TO_BIN(UUID()), 'NO_FILE_DAY_DELETE', 'Remove dias sem Arquivos'),
  (UUID_TO_BIN(UUID()), 'NO_FILE_DAY_ACTIVE_OR_INACTIVE', 'Ativa ou Inativa dias sem Arquivos'),

  (UUID_TO_BIN(UUID()), 'BANKS_CONSULT', 'Lista Bancos'),
  (UUID_TO_BIN(UUID()), 'BANKS_CREATE', 'Cadastra Bancos'),
  (UUID_TO_BIN(UUID()), 'BANKS_CHANGE', 'Altera Bancos'),
  (UUID_TO_BIN(UUID()), 'BANKS_ACTIVE_OR_INACTIVE', 'Ativa ou Inativa Bancos');

INSERT INTO cs_groups_permissions (group_id, permission_id) VALUES
  ((SELECT id FROM cs_groups WHERE name = 'ADMINISTRADOR'), (SELECT id FROM cs_permissions WHERE name = 'ANTICIPATION_CONSULT')),
  ((SELECT id FROM cs_groups WHERE name = 'ADMINISTRADOR'), (SELECT id FROM cs_permissions WHERE name = 'CREDIT_ORDER_CONSULT')),
  ((SELECT id FROM cs_groups WHERE name = 'ADMINISTRADOR'), (SELECT id FROM cs_permissions WHERE name = 'BANK_STATEMENT_CONSULT')),
  ((SELECT id FROM cs_groups WHERE name = 'ADMINISTRADOR'), (SELECT id FROM cs_permissions WHERE name = 'SALES_SUMMARY_CONSULT')),

  ((SELECT id FROM cs_groups WHERE name = 'ADMINISTRADOR'), (SELECT id FROM cs_permissions WHERE name = 'HOLIDAYS_CONSULT')),
  ((SELECT id FROM cs_groups WHERE name = 'ADMINISTRADOR'), (SELECT id FROM cs_permissions WHERE name = 'HOLIDAYS_CREATE')),
  ((SELECT id FROM cs_groups WHERE name = 'ADMINISTRADOR'), (SELECT id FROM cs_permissions WHERE name = 'HOLIDAYS_CHANGE')),
  ((SELECT id FROM cs_groups WHERE name = 'ADMINISTRADOR'), (SELECT id FROM cs_permissions WHERE name = 'HOLIDAYS_ACTIVE_OR_INACTIVE')),

  ((SELECT id FROM cs_groups WHERE name = 'ADMINISTRADOR'), (SELECT id FROM cs_permissions WHERE name = 'BANKING_DOMICILE_CONSULT')),
  ((SELECT id FROM cs_groups WHERE name = 'ADMINISTRADOR'), (SELECT id FROM cs_permissions WHERE name = 'BANKING_DOMICILE_CREATE')),
  ((SELECT id FROM cs_groups WHERE name = 'ADMINISTRADOR'), (SELECT id FROM cs_permissions WHERE name = 'BANKING_DOMICILE_CHANGE')),
  ((SELECT id FROM cs_groups WHERE name = 'ADMINISTRADOR'), (SELECT id FROM cs_permissions WHERE name = 'BANKING_DOMICILE_ACTIVE_OR_INACTIVE')),

  ((SELECT id FROM cs_groups WHERE name = 'ADMINISTRADOR'), (SELECT id FROM cs_permissions WHERE name = 'NO_FILE_DAY_DELETE')),
  ((SELECT id FROM cs_groups WHERE name = 'ADMINISTRADOR'), (SELECT id FROM cs_permissions WHERE name = 'NO_FILE_DAY_CONSULT')),
  ((SELECT id FROM cs_groups WHERE name = 'ADMINISTRADOR'), (SELECT id FROM cs_permissions WHERE name = 'NO_FILE_DAY_CREATE')),
  ((SELECT id FROM cs_groups WHERE name = 'ADMINISTRADOR'), (SELECT id FROM cs_permissions WHERE name = 'NO_FILE_DAY_CHANGE')),
  ((SELECT id FROM cs_groups WHERE name = 'ADMINISTRADOR'), (SELECT id FROM cs_permissions WHERE name = 'NO_FILE_DAY_ACTIVE_OR_INACTIVE')),

  ((SELECT id FROM cs_groups WHERE name = 'ADMINISTRADOR'), (SELECT id FROM cs_permissions WHERE name = 'BANKS_CONSULT')),
  ((SELECT id FROM cs_groups WHERE name = 'ADMINISTRADOR'), (SELECT id FROM cs_permissions WHERE name = 'BANKS_CREATE')),
  ((SELECT id FROM cs_groups WHERE name = 'ADMINISTRADOR'), (SELECT id FROM cs_permissions WHERE name = 'BANKS_CHANGE')),
  ((SELECT id FROM cs_groups WHERE name = 'ADMINISTRADOR'), (SELECT id FROM cs_permissions WHERE name = 'BANKS_ACTIVE_OR_INACTIVE'));
