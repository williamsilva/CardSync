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

INSERT INTO cs_permissions (id, name, description)
SELECT UUID_TO_BIN(UUID()), 'FILE_PROCESSING_READ', 'Consulta arquivos processados e erros de importação'
WHERE NOT EXISTS (SELECT 1 FROM cs_permissions WHERE name = 'FILE_PROCESSING_READ');

INSERT INTO cs_permissions (id, name, description)
SELECT UUID_TO_BIN(UUID()), 'FILE_PROCESSING_PROCESS', 'Processa arquivos de importação'
WHERE NOT EXISTS (SELECT 1 FROM cs_permissions WHERE name = 'FILE_PROCESSING_PROCESS');

INSERT INTO cs_permissions (id, name, description)
SELECT UUID_TO_BIN(UUID()), 'FILE_PROCESSING_REPROCESS', 'Reprocessa pendências de importação'
WHERE NOT EXISTS (SELECT 1 FROM cs_permissions WHERE name = 'FILE_PROCESSING_REPROCESS');

INSERT INTO cs_groups_permissions (group_id, permission_id)
SELECT g.id, p.id
FROM cs_groups g
JOIN cs_permissions p ON p.name IN ('FILE_PROCESSING_READ', 'FILE_PROCESSING_PROCESS', 'FILE_PROCESSING_REPROCESS')
WHERE g.name = 'ADMINISTRADOR'
  AND NOT EXISTS (
    SELECT 1 FROM cs_groups_permissions gp
    WHERE gp.group_id = g.id AND gp.permission_id = p.id
  );
