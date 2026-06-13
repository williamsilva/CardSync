ALTER TABLE cs_processed_file ADD COLUMN content_hash VARCHAR(64) NULL AFTER file_name;

CREATE UNIQUE INDEX uq_cs_processed_file_content_hash ON cs_processed_file (content_hash);

INSERT INTO cs_permissions (id, name, description) VALUES
  (UUID_TO_BIN(UUID()), 'ANTICIPATION_CONSULT', 'Lista transações Antecipadas'),
  (UUID_TO_BIN(UUID()), 'CREDIT_ORDER_CONSULT', 'Lista Ordens de pagamento'),
  (UUID_TO_BIN(UUID()), 'BANK_STATEMENT_CONSULT', 'Lista lançamentos bancários'),
  (UUID_TO_BIN(UUID()), 'SALES_SUMMARY_CONSULT', 'Lista Resumo de vendas');

INSERT INTO cs_groups_permissions (group_id, permission_id) VALUES
  ((SELECT id FROM cs_groups WHERE name = 'ADMINISTRADOR'), (SELECT id FROM cs_permissions WHERE name = 'ANTICIPATION_CONSULT')),
  ((SELECT id FROM cs_groups WHERE name = 'ADMINISTRADOR'), (SELECT id FROM cs_permissions WHERE name = 'CREDIT_ORDER_CONSULT')),
  ((SELECT id FROM cs_groups WHERE name = 'ADMINISTRADOR'), (SELECT id FROM cs_permissions WHERE name = 'BANK_STATEMENT_CONSULT')),
  ((SELECT id FROM cs_groups WHERE name = 'ADMINISTRADOR'), (SELECT id FROM cs_permissions WHERE name = 'SALES_SUMMARY_CONSULT'));
