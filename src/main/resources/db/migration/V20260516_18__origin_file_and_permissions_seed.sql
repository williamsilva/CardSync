INSERT INTO cs_origin_file (id, created_at, updated_at, code, name, description)
SELECT gen_random_uuid(), CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), 'ERP', 'ERP', 'Arquivos CSV do ERP'
WHERE NOT EXISTS (SELECT 1 FROM cs_origin_file WHERE code = 'ERP');

INSERT INTO cs_origin_file (id, created_at, updated_at, code, name, description)
SELECT gen_random_uuid(), CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), 'REDE', 'Rede', 'Arquivos EEVC/EEFI da adquirente Rede'
WHERE NOT EXISTS (SELECT 1 FROM cs_origin_file WHERE code = 'REDE');

INSERT INTO cs_origin_file (id, created_at, updated_at, code, name, description)
SELECT gen_random_uuid(), CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), 'CIELO', 'Cielo', 'Arquivos da adquirente Cielo'
WHERE NOT EXISTS (SELECT 1 FROM cs_origin_file WHERE code = 'CIELO');

INSERT INTO cs_origin_file (id, created_at, updated_at, code, name, description)
SELECT gen_random_uuid(), CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), 'STONE', 'Stone', 'Arquivos da adquirente Stone'
WHERE NOT EXISTS (SELECT 1 FROM cs_origin_file WHERE code = 'STONE');

INSERT INTO cs_origin_file (id, created_at, updated_at, code, name, description)
SELECT gen_random_uuid(), CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), 'SANTANDER', 'Santander', 'Arquivos bancários Santander'
WHERE NOT EXISTS (SELECT 1 FROM cs_origin_file WHERE code = 'SANTANDER');

INSERT INTO cs_origin_file (id, created_at, updated_at, code, name, description)
SELECT gen_random_uuid(), CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), 'ITAU', 'Itaú', 'Arquivos bancários Itaú'
WHERE NOT EXISTS (SELECT 1 FROM cs_origin_file WHERE code = 'ITAU');

INSERT INTO cs_origin_file (id, created_at, updated_at, code, name, description)
SELECT gen_random_uuid(), CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), 'BRADESCO', 'Bradesco', 'Arquivos bancários Bradesco'
WHERE NOT EXISTS (SELECT 1 FROM cs_origin_file WHERE code = 'BRADESCO');
