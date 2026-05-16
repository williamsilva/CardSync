INSERT INTO cs_origin_file (id, created_at, updated_at, code, name, description)
SELECT UUID_TO_BIN(UUID()), CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), 'ERP', 'ERP', 'Arquivos CSV do ERP'
WHERE NOT EXISTS (SELECT 1 FROM cs_origin_file WHERE code = 'ERP');

INSERT INTO cs_origin_file (id, created_at, updated_at, code, name, description)
SELECT UUID_TO_BIN(UUID()), CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), 'REDE', 'Rede', 'Arquivos EEVC/EEFI da adquirente Rede'
WHERE NOT EXISTS (SELECT 1 FROM cs_origin_file WHERE code = 'REDE');

INSERT INTO cs_origin_file (id, created_at, updated_at, code, name, description)
SELECT UUID_TO_BIN(UUID()), CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), 'CIELO', 'Cielo', 'Arquivos da adquirente Cielo'
WHERE NOT EXISTS (SELECT 1 FROM cs_origin_file WHERE code = 'CIELO');

INSERT INTO cs_origin_file (id, created_at, updated_at, code, name, description)
SELECT UUID_TO_BIN(UUID()), CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), 'STONE', 'Stone', 'Arquivos da adquirente Stone'
WHERE NOT EXISTS (SELECT 1 FROM cs_origin_file WHERE code = 'STONE');

INSERT INTO cs_origin_file (id, created_at, updated_at, code, name, description)
SELECT UUID_TO_BIN(UUID()), CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), 'SANTANDER', 'Santander', 'Arquivos bancários Santander'
WHERE NOT EXISTS (SELECT 1 FROM cs_origin_file WHERE code = 'SANTANDER');

INSERT INTO cs_origin_file (id, created_at, updated_at, code, name, description)
SELECT UUID_TO_BIN(UUID()), CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), 'ITAU', 'Itaú', 'Arquivos bancários Itaú'
WHERE NOT EXISTS (SELECT 1 FROM cs_origin_file WHERE code = 'ITAU');

INSERT INTO cs_origin_file (id, created_at, updated_at, code, name, description)
SELECT UUID_TO_BIN(UUID()), CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), 'BRADESCO', 'Bradesco', 'Arquivos bancários Bradesco'
WHERE NOT EXISTS (SELECT 1 FROM cs_origin_file WHERE code = 'BRADESCO');

INSERT INTO cs_permissions (id, name, description) VALUES
  (UUID_TO_BIN(UUID()), 'ERP_INSTALLMENTS_CHANGE', 'Altera parcelas ERP'), (UUID_TO_BIN(UUID()), 'ERP_INSTALLMENTS_CREATE', 'Cadastra parcelas ERP'),
  (UUID_TO_BIN(UUID()), 'ERP_INSTALLMENTS_CONSULT', 'Consulta parcelas ERP'), (UUID_TO_BIN(UUID()), 'ERP_INSTALLMENTS_DELETE', 'Excluir parcelas ERP'),
  (UUID_TO_BIN(UUID()), 'ERP_INSTALLMENTS_ACTIVE_OR_INACTIVE', 'Ativa ou desativa parcelas ERP'),
  (UUID_TO_BIN(UUID()), 'ERP_SALES_CHANGE', 'Altera vendas ERP'), (UUID_TO_BIN(UUID()), 'ERP_SALES_CREATE', 'Cadastra vendas ERP'),
  (UUID_TO_BIN(UUID()), 'ERP_SALES_CONSULT', 'Consulta vendas ERP'), (UUID_TO_BIN(UUID()), 'ERP_SALES_DELETE', 'Excluir vendas ERP'),
  (UUID_TO_BIN(UUID()), 'ERP_SALES_ACTIVE_OR_INACTIVE', 'Ativa ou desativa vendas ERP'),
  (UUID_TO_BIN(UUID()), 'ACQUIRERS_INSTALLMENTS_CHANGE', 'Altera parcelas Adquirentes'), (UUID_TO_BIN(UUID()), 'ACQUIRERS_INSTALLMENTS_CREATE', 'Cadastra parcelas Adquirentes'),
  (UUID_TO_BIN(UUID()), 'ACQUIRERS_INSTALLMENTS_CONSULT', 'Consulta parcelas Adquirentes'), (UUID_TO_BIN(UUID()), 'ACQUIRERS_INSTALLMENTS_DELETE', 'Excluir parcelas Adquirentes'),
  (UUID_TO_BIN(UUID()), 'ACQUIRERS_INSTALLMENTS_ACTIVE_OR_INACTIVE', 'Ativa ou desativa parcelas Adquirentes'),
  (UUID_TO_BIN(UUID()), 'ACQUIRERS_SALES_CHANGE', 'Altera vendas Adquirentes'), (UUID_TO_BIN(UUID()), 'ACQUIRERS_SALES_CREATE', 'Cadastra vendas Adquirentes'),
  (UUID_TO_BIN(UUID()), 'ACQUIRERS_SALES_CONSULT', 'Consulta vendas Adquirentes'), (UUID_TO_BIN(UUID()), 'ACQUIRERS_SALES_DELETE', 'Excluir vendas Adquirentes'),
  (UUID_TO_BIN(UUID()), 'ACQUIRERS_SALES_ACTIVE_OR_INACTIVE', 'Ativa ou desativa vendas Adquirentes'),

  (UUID_TO_BIN(UUID()), 'FILE_PROCESSING_READ', 'Consulta arquivos processados e erros de importação'),
  (UUID_TO_BIN(UUID()), 'FILE_PROCESSING_PROCESS', 'Processa arquivos de importação'),
  (UUID_TO_BIN(UUID()), 'FILE_PROCESSING_REPROCESS', 'Reprocessa pendências de importação');

INSERT INTO cs_groups_permissions (group_id, permission_id) VALUES
  ((SELECT id FROM cs_groups WHERE name = 'ADMINISTRADOR'), (SELECT id FROM cs_permissions WHERE name = 'ERP_INSTALLMENTS_CHANGE')),
  ((SELECT id FROM cs_groups WHERE name = 'ADMINISTRADOR'), (SELECT id FROM cs_permissions WHERE name = 'ERP_INSTALLMENTS_CONSULT')),
  ((SELECT id FROM cs_groups WHERE name = 'ADMINISTRADOR'), (SELECT id FROM cs_permissions WHERE name = 'ERP_INSTALLMENTS_CREATE')),
  ((SELECT id FROM cs_groups WHERE name = 'ADMINISTRADOR'), (SELECT id FROM cs_permissions WHERE name = 'ERP_INSTALLMENTS_DELETE')),
  ((SELECT id FROM cs_groups WHERE name = 'ADMINISTRADOR'), (SELECT id FROM cs_permissions WHERE name = 'ERP_INSTALLMENTS_ACTIVE_OR_INACTIVE')),
  ((SELECT id FROM cs_groups WHERE name = 'ADMINISTRADOR'), (SELECT id FROM cs_permissions WHERE name = 'ERP_SALES_CHANGE')),
  ((SELECT id FROM cs_groups WHERE name = 'ADMINISTRADOR'), (SELECT id FROM cs_permissions WHERE name = 'ERP_SALES_CONSULT')),
  ((SELECT id FROM cs_groups WHERE name = 'ADMINISTRADOR'), (SELECT id FROM cs_permissions WHERE name = 'ERP_SALES_CREATE')),
  ((SELECT id FROM cs_groups WHERE name = 'ADMINISTRADOR'), (SELECT id FROM cs_permissions WHERE name = 'ERP_SALES_DELETE')),
  ((SELECT id FROM cs_groups WHERE name = 'ADMINISTRADOR'), (SELECT id FROM cs_permissions WHERE name = 'ERP_SALES_ACTIVE_OR_INACTIVE')),
  ((SELECT id FROM cs_groups WHERE name = 'ADMINISTRADOR'), (SELECT id FROM cs_permissions WHERE name = 'ACQUIRERS_INSTALLMENTS_CHANGE')),
  ((SELECT id FROM cs_groups WHERE name = 'ADMINISTRADOR'), (SELECT id FROM cs_permissions WHERE name = 'ACQUIRERS_INSTALLMENTS_CONSULT')),
  ((SELECT id FROM cs_groups WHERE name = 'ADMINISTRADOR'), (SELECT id FROM cs_permissions WHERE name = 'ACQUIRERS_INSTALLMENTS_CREATE')),
  ((SELECT id FROM cs_groups WHERE name = 'ADMINISTRADOR'), (SELECT id FROM cs_permissions WHERE name = 'ACQUIRERS_INSTALLMENTS_DELETE')),
  ((SELECT id FROM cs_groups WHERE name = 'ADMINISTRADOR'), (SELECT id FROM cs_permissions WHERE name = 'ACQUIRERS_INSTALLMENTS_ACTIVE_OR_INACTIVE')),
  ((SELECT id FROM cs_groups WHERE name = 'ADMINISTRADOR'), (SELECT id FROM cs_permissions WHERE name = 'ACQUIRERS_SALES_CHANGE')),
  ((SELECT id FROM cs_groups WHERE name = 'ADMINISTRADOR'), (SELECT id FROM cs_permissions WHERE name = 'ACQUIRERS_SALES_CONSULT')),
  ((SELECT id FROM cs_groups WHERE name = 'ADMINISTRADOR'), (SELECT id FROM cs_permissions WHERE name = 'ACQUIRERS_SALES_CREATE')),
  ((SELECT id FROM cs_groups WHERE name = 'ADMINISTRADOR'), (SELECT id FROM cs_permissions WHERE name = 'ACQUIRERS_SALES_DELETE')),
  ((SELECT id FROM cs_groups WHERE name = 'ADMINISTRADOR'), (SELECT id FROM cs_permissions WHERE name = 'ACQUIRERS_SALES_ACTIVE_OR_INACTIVE')),

  ((SELECT id FROM cs_groups WHERE name = 'ADMINISTRADOR'), (SELECT id FROM cs_permissions WHERE name = 'FILE_PROCESSING_READ')),
  ((SELECT id FROM cs_groups WHERE name = 'ADMINISTRADOR'), (SELECT id FROM cs_permissions WHERE name = 'FILE_PROCESSING_PROCESS')),
  ((SELECT id FROM cs_groups WHERE name = 'ADMINISTRADOR'), (SELECT id FROM cs_permissions WHERE name = 'FILE_PROCESSING_REPROCESS'));
