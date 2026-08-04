-- Correção da Tabela III (Códigos de Bandeira) da Cielo — a migration anterior
-- (V20260803_01) foi montada a partir de uma extração de PDF (pdftotext -layout) cuja lógica de
-- alinhamento de colunas confundiu essa tabela específica, deslocando toda descrição de bandeira
-- uma posição adiante do código real (ex.: "002 Visa" quando o correto é "001 Visa").
--
-- Confirmado por duas fontes independentes:
-- 1) Faixas de BIN reais dos cartões nas transações já importadas: código "001" é 100% BIN
--    começando em "4" (Visa); código "002" é BIN "5"/"2" (Mastercard); código "003" é BIN "3"
--    (American Express) — nenhum bate com a bandeira que a migration anterior atribuía a esses
--    códigos.
-- 2) Re-extração do mesmo PDF em modo "-raw" (ordem do fluxo de conteúdo, não afetada pela
--    heurística de colunas do "-layout"), que lista a tabela corretamente alinhada.
--
-- Apaga e reinsere do zero (mais simples que UPDATE, já que os próprios códigos mudam de posição,
-- não só as descrições).
DELETE FROM cs_flag_acquirer
WHERE acquirer_id = (SELECT id FROM cs_acquirer WHERE file_identifier = 'Cielo');

INSERT INTO cs_flag_acquirer (id, acquirer_id, flag_id, acquirer_code) VALUES
    (gen_random_uuid(), (SELECT id FROM cs_acquirer WHERE file_identifier = 'Cielo'), (SELECT id FROM cs_flag WHERE name = 'Visa'), '001'),
    (gen_random_uuid(), (SELECT id FROM cs_acquirer WHERE file_identifier = 'Cielo'), (SELECT id FROM cs_flag WHERE name = 'Mastercard'), '002'),
    (gen_random_uuid(), (SELECT id FROM cs_acquirer WHERE file_identifier = 'Cielo'), (SELECT id FROM cs_flag WHERE name = 'American Express'), '003'),
    (gen_random_uuid(), (SELECT id FROM cs_acquirer WHERE file_identifier = 'Cielo'), (SELECT id FROM cs_flag WHERE name = 'Sorocred'), '006'),
    (gen_random_uuid(), (SELECT id FROM cs_acquirer WHERE file_identifier = 'Cielo'), (SELECT id FROM cs_flag WHERE name = 'Elo'), '007'),
    (gen_random_uuid(), (SELECT id FROM cs_acquirer WHERE file_identifier = 'Cielo'), (SELECT id FROM cs_flag WHERE name = 'Diners Club'), '009'),
    (gen_random_uuid(), (SELECT id FROM cs_acquirer WHERE file_identifier = 'Cielo'), (SELECT id FROM cs_flag WHERE name = 'Banescard'), '015'),
    (gen_random_uuid(), (SELECT id FROM cs_acquirer WHERE file_identifier = 'Cielo'), (SELECT id FROM cs_flag WHERE name = 'Cabal'), '023'),
    (gen_random_uuid(), (SELECT id FROM cs_acquirer WHERE file_identifier = 'Cielo'), (SELECT id FROM cs_flag WHERE name = 'CUP'), '027'),
    (gen_random_uuid(), (SELECT id FROM cs_acquirer WHERE file_identifier = 'Cielo'), (SELECT id FROM cs_flag WHERE name = 'Credsystem (Mais)'), '029'),
    (gen_random_uuid(), (SELECT id FROM cs_acquirer WHERE file_identifier = 'Cielo'), (SELECT id FROM cs_flag WHERE name = 'Hipercard'), '040'),
    (gen_random_uuid(), (SELECT id FROM cs_acquirer WHERE file_identifier = 'Cielo'), (SELECT id FROM cs_flag WHERE name = 'JCB'), '060'),
    (gen_random_uuid(), (SELECT id FROM cs_acquirer WHERE file_identifier = 'Cielo'), (SELECT id FROM cs_flag WHERE name = 'Credz'), '064'),
    (gen_random_uuid(), (SELECT id FROM cs_acquirer WHERE file_identifier = 'Cielo'), (SELECT id FROM cs_flag WHERE name = 'Avista'), '069'),
    (gen_random_uuid(), (SELECT id FROM cs_acquirer WHERE file_identifier = 'Cielo'), (SELECT id FROM cs_flag WHERE name = 'Pix'), '888');

-- Códigos sem bandeira cadastrada no CardSync ficam de fora por ora (004=TicketLog,
-- 011=Agiplan, 035=Explanada, 038=Good Card, 057=Verdecard, 072=Hiper, 075=Ourocard) —
-- FileLookupService já trata ausência de bandeira sem quebrar o import.
