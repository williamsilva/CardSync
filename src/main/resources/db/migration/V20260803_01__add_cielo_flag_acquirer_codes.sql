-- Mapeamento de bandeiras da Cielo (Tabela III do manual "Extrato Eletrônico v15.15"), usado por
-- FileLookupService.flagByAcquirerCode ao importar CIELO03 (ver ProcessCielo03Service). O
-- acquirer "Cielo S/A" e a origem de arquivo "CIELO" já existiam (V20260516_07/18); só faltava
-- este vínculo bandeira x código, hoje com 0 linhas para a Cielo.
--
-- Códigos sem bandeira cadastrada no CardSync ficam de fora por ora (015 Agiplan/Banescard,
-- 038 Explanada, 040 Good Card, 060 Verdecard, 075 Hiper, 888 Ourocard/Pix) — FileLookupService
-- já trata ausência de bandeira sem quebrar o import (fica null, visível pra reclassificar depois).
--
-- Códigos da Cielo têm 3 dígitos (ex.: '002'), diferente do Rede (1 caractere) — a coluna estava
-- dimensionada só para o Rede.
ALTER TABLE cs_flag_acquirer ALTER COLUMN acquirer_code TYPE VARCHAR(3);

INSERT INTO cs_flag_acquirer (id, acquirer_id, flag_id, acquirer_code) VALUES
    (gen_random_uuid(), (SELECT id FROM cs_acquirer WHERE file_identifier = 'Cielo'), (SELECT id FROM cs_flag WHERE name = 'Visa'), '002'),
    (gen_random_uuid(), (SELECT id FROM cs_acquirer WHERE file_identifier = 'Cielo'), (SELECT id FROM cs_flag WHERE name = 'Mastercard'), '003'),
    (gen_random_uuid(), (SELECT id FROM cs_acquirer WHERE file_identifier = 'Cielo'), (SELECT id FROM cs_flag WHERE name = 'American Express'), '004'),
    (gen_random_uuid(), (SELECT id FROM cs_acquirer WHERE file_identifier = 'Cielo'), (SELECT id FROM cs_flag WHERE name = 'Sorocred'), '007'),
    (gen_random_uuid(), (SELECT id FROM cs_acquirer WHERE file_identifier = 'Cielo'), (SELECT id FROM cs_flag WHERE name = 'Elo'), '009'),
    (gen_random_uuid(), (SELECT id FROM cs_acquirer WHERE file_identifier = 'Cielo'), (SELECT id FROM cs_flag WHERE name = 'Diners Club'), '011'),
    (gen_random_uuid(), (SELECT id FROM cs_acquirer WHERE file_identifier = 'Cielo'), (SELECT id FROM cs_flag WHERE name = 'Cabal'), '027'),
    (gen_random_uuid(), (SELECT id FROM cs_acquirer WHERE file_identifier = 'Cielo'), (SELECT id FROM cs_flag WHERE name = 'CUP'), '029'),
    (gen_random_uuid(), (SELECT id FROM cs_acquirer WHERE file_identifier = 'Cielo'), (SELECT id FROM cs_flag WHERE name = 'Credsystem (Mais)'), '035'),
    (gen_random_uuid(), (SELECT id FROM cs_acquirer WHERE file_identifier = 'Cielo'), (SELECT id FROM cs_flag WHERE name = 'Hipercard'), '057'),
    (gen_random_uuid(), (SELECT id FROM cs_acquirer WHERE file_identifier = 'Cielo'), (SELECT id FROM cs_flag WHERE name = 'JCB'), '064'),
    (gen_random_uuid(), (SELECT id FROM cs_acquirer WHERE file_identifier = 'Cielo'), (SELECT id FROM cs_flag WHERE name = 'Credz'), '069'),
    (gen_random_uuid(), (SELECT id FROM cs_acquirer WHERE file_identifier = 'Cielo'), (SELECT id FROM cs_flag WHERE name = 'Avista'), '072');
