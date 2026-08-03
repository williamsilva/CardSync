-- Cria um "Dia sem Arquivo" (grupo Banco, tipo Sem Movimento) para cada dia útil em que os
-- lançamentos do extrato Santander da Acquamania foram incluídos manualmente
-- (ver insert-santander-acquamania-releases-2026-03.sql) — mesmo padrão do formulário "Novo Dia
-- sem Arquivo" (Domicílio Bancário = Santander Ag 3346/Cc 13005859, Grupo = Banco, Tipo = Sem
-- Movimento, Descrição = "Banco não enviou extrato"), só que sem precisar repetir na tela pra
-- cada um dos 5 dias.
--
-- Domicílio resolvido por chave de negócio (CNPJ + código do banco + agência/conta) — não por
-- UUID fixo, que muda a cada reset do banco. Ver insert-santander-acquamania-releases-2026-03.sql
-- pra mais contexto.

BEGIN;

CREATE TEMP TABLE tmp_no_file_days (
  no_file_date date NOT NULL
) ON COMMIT DROP;

INSERT INTO tmp_no_file_days (no_file_date) VALUES
  ('2026-03-25'),
  ('2026-03-26'),
  ('2026-03-27'),
  ('2026-03-30'),
  ('2026-03-31');

CREATE TEMP TABLE tmp_ctx ON COMMIT DROP AS
SELECT bd.id AS banking_domicile_id
FROM cs_company c
JOIN cs_banking_domicile bd ON bd.company_id = c.id
JOIN cs_bank b ON b.id = bd.bank_id
WHERE c.cnpj = '39303847000180'
  AND b.code = '033'
  AND bd.agency = 3346
  AND bd.current_account = 13005859;

DO $$
DECLARE
  ctx_count integer;
BEGIN
  SELECT count(*) INTO ctx_count FROM tmp_ctx;
  IF ctx_count <> 1 THEN
    RAISE EXCEPTION 'Esperava 1 domicilio bancario, encontrou %. Confira: empresa CNPJ 39303847000180, domicilio Santander (codigo 033) Ag 3346/Cc 13005859.', ctx_count;
  END IF;
END $$;

INSERT INTO cs_no_file_day (
  id, no_file_date, description, day_type, file_group, banking_domicile_id, status, created_at
)
SELECT
  gen_random_uuid(),
  t.no_file_date,
  'Banco não enviou extrato',
  1, -- NoFileDayTypeEnum.NO_MOVEMENT
  'BANK',
  ctx.banking_domicile_id,
  1, -- StatusEnum.ACTIVE
  now()
FROM tmp_no_file_days t
CROSS JOIN tmp_ctx ctx
WHERE NOT EXISTS (
  SELECT 1 FROM cs_no_file_day nfd
  WHERE nfd.banking_domicile_id = ctx.banking_domicile_id
    AND nfd.no_file_date = t.no_file_date
    AND nfd.file_group = 'BANK'
);

-- Diagnóstico
SELECT count(*) AS total_dias_no_lote FROM tmp_no_file_days;

SELECT count(*) AS ja_existentes_pulados
FROM tmp_no_file_days t
CROSS JOIN tmp_ctx ctx
WHERE EXISTS (
  SELECT 1 FROM cs_no_file_day nfd
  WHERE nfd.banking_domicile_id = ctx.banking_domicile_id
    AND nfd.no_file_date = t.no_file_date
    AND nfd.file_group = 'BANK'
);

COMMIT;
