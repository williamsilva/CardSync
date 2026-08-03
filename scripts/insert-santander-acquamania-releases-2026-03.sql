-- Inclui os lançamentos de "Pagamento Cartao De Credito/Debito REDE-*" do extrato Santander
-- anexado (ACQUAMANIA MULTIPLO LAZER S/A, Ag 3346 / Cc 130058595), baseado no PDF
-- "santander acqua 05-31.pdf". Só as linhas de cartão (mesmo escopo do Lançamento Bancário
-- Manual: RECEIPT + CASH_DEBIT/CASH_CREDIT) — PIX, TED, boleto e tarifas ficam de fora, não são
-- lançamento de cartão.
--
-- IMPORTANTE: o extrato anexado só tem dados até 31/03/2026 — não tem nada de abril a junho,
-- mesmo o pedido original tendo sido "25/03 a 31/06". Este script cobre só o período que existe
-- de fato no arquivo (25 a 31/03/2026, dias úteis: 28-29/03 é fim de semana, sem lançamento).
-- Quando tiver o extrato de abril-junho, gerar um script novo no mesmo padrão.
--
-- O Santander não coloca o PV colado ao lançamento (diferente do formato Itaú/Rede visto em
-- outro extrato, ex.: "CD0007866470") — establishment_id fica NULL em todas as linhas, mesmo
-- comportamento do ManualBankStatementTextImportService quando o texto não tem PV extraível.
--
-- Empresa/domicílio/banco/adquirente resolvidos por CHAVE DE NEGÓCIO (não UUID fixo) — os IDs
-- mudam a cada reset do banco:
--   empresa    = CNPJ 39303847000180 (Acquamania Multiplo Lazer)
--   banco      = código 033 (Santander)
--   domicílio  = agência 3346 / conta 13005859 (a mesma empresa+banco acima)
--   adquirente = fantasy_name começando com "Rede"
--   bandeira   = nome exato (Elo/Mastercard/Visa/American Express)
--
-- Duplicidade: mesma chave usada por ReleasesBankService#createManual (domicílio + data + valor
-- + modalidade + adquirente + bandeira + estabelecimento) — rodar de novo não duplica.

BEGIN;

CREATE TEMP TABLE tmp_santander_releases (
  release_date date NOT NULL,
  description varchar(255) NOT NULL,
  release_value numeric(18,8) NOT NULL,
  modality_payment_bank integer NOT NULL, -- 1 = CASH_DEBIT, 2 = CASH_CREDIT
  flag_name text NOT NULL
) ON COMMIT DROP;

INSERT INTO tmp_santander_releases (release_date, description, release_value, modality_payment_bank, flag_name) VALUES
  -- 25/03/2026
  ('2026-03-25', 'Pagamento Cartao De Credito REDE-ELO',         329.99,   2, 'Elo'),
  ('2026-03-25', 'Pagamento Cartao De Credito REDE-ELO',        3112.11,   2, 'Elo'),
  ('2026-03-25', 'Pagamento Cartao De Credito REDE-MASTER',      2512.69,  2, 'Mastercard'),
  ('2026-03-25', 'Pagamento Cartao De Credito REDE-MASTER',     39143.42,  2, 'Mastercard'),
  ('2026-03-25', 'Pagamento Cartao De Credito REDE-VISA',        2971.86,  2, 'Visa'),
  ('2026-03-25', 'Pagamento Cartao De Credito REDE-VISA',       25178.35,  2, 'Visa'),
  ('2026-03-25', 'Pagamento Cartao De Debito REDE-MAESTRO',         69.32, 1, 'Mastercard'),
  ('2026-03-25', 'Pagamento Cartao De Debito REDE-VISA ELECTR',     14.85, 1, 'Visa'),
  -- 26/03/2026
  ('2026-03-26', 'Pagamento Cartao De Credito REDE-ELO',           86.09,  2, 'Elo'),
  ('2026-03-26', 'Pagamento Cartao De Credito REDE-ELO',          366.66,  2, 'Elo'),
  ('2026-03-26', 'Pagamento Cartao De Credito REDE-MASTER',      5528.47,  2, 'Mastercard'),
  ('2026-03-26', 'Pagamento Cartao De Credito REDE-MASTER',      2670.51,  2, 'Mastercard'),
  ('2026-03-26', 'Pagamento Cartao De Credito REDE-VISA',        3879.02,  2, 'Visa'),
  ('2026-03-26', 'Pagamento Cartao De Credito REDE-VISA',        6001.14,  2, 'Visa'),
  -- 27/03/2026
  ('2026-03-27', 'Pagamento Cartao De Credito REDE-AMEX',         143.48,  2, 'American Express'),
  ('2026-03-27', 'Pagamento Cartao De Credito REDE-ELO',          307.63,  2, 'Elo'),
  ('2026-03-27', 'Pagamento Cartao De Credito REDE-ELO',          373.05,  2, 'Elo'),
  ('2026-03-27', 'Pagamento Cartao De Credito REDE-MASTER',      5347.38,  2, 'Mastercard'),
  ('2026-03-27', 'Pagamento Cartao De Credito REDE-MASTER',      2356.08,  2, 'Mastercard'),
  ('2026-03-27', 'Pagamento Cartao De Credito REDE-VISA',        5433.94,  2, 'Visa'),
  ('2026-03-27', 'Pagamento Cartao De Credito REDE-VISA',        2566.28,  2, 'Visa'),
  ('2026-03-27', 'Pagamento Cartao De Debito REDE-VISA ELECTR',     7.92,  1, 'Visa'),
  -- 30/03/2026
  ('2026-03-30', 'Pagamento Cartao De Credito REDE-AMEX',         200.87,  2, 'American Express'),
  ('2026-03-30', 'Pagamento Cartao De Credito REDE-ELO',         1197.02,  2, 'Elo'),
  ('2026-03-30', 'Pagamento Cartao De Credito REDE-ELO',          352.63,  2, 'Elo'),
  ('2026-03-30', 'Pagamento Cartao De Credito REDE-MASTER',     12086.10,  2, 'Mastercard'),
  ('2026-03-30', 'Pagamento Cartao De Credito REDE-MASTER',      8847.50,  2, 'Mastercard'),
  ('2026-03-30', 'Pagamento Cartao De Credito REDE-VISA',        8738.49,  2, 'Visa'),
  ('2026-03-30', 'Pagamento Cartao De Credito REDE-VISA',        7330.67,  2, 'Visa'),
  ('2026-03-30', 'Pagamento Cartao De Debito REDE-ELO DEBITO',   4487.47,  1, 'Elo'),
  -- 31/03/2026
  ('2026-03-31', 'Pagamento Cartao De Credito REDE-AMEX',           7.72,  2, 'American Express'),
  ('2026-03-31', 'Pagamento Cartao De Credito REDE-ELO',          225.56,  2, 'Elo'),
  ('2026-03-31', 'Pagamento Cartao De Credito REDE-ELO',         1123.19,  2, 'Elo');

-- Resolve empresa/domicílio/banco/adquirente por chave de negócio — não por UUID fixo, que
-- muda a cada reset do banco.
CREATE TEMP TABLE tmp_ctx ON COMMIT DROP AS
SELECT
  c.id  AS company_id,
  bd.id AS banking_domicile_id,
  b.id  AS bank_id,
  a.id  AS acquirer_id
FROM cs_company c
JOIN cs_banking_domicile bd ON bd.company_id = c.id
JOIN cs_bank b ON b.id = bd.bank_id
CROSS JOIN cs_acquirer a
WHERE c.cnpj = '39303847000180'
  AND b.code = '033'
  AND bd.agency = 3346
  AND bd.current_account = 13005859
  AND a.fantasy_name ILIKE 'Rede%';

-- Falha alto e claro se a chave de negócio não resolver pra exatamente 1 combinação (empresa/
-- domicílio/banco/adquirente ausente ou duplicado) — evita inserir com contexto errado ou
-- silenciosamente não inserir nada.
DO $$
DECLARE
  ctx_count integer;
BEGIN
  SELECT count(*) INTO ctx_count FROM tmp_ctx;
  IF ctx_count <> 1 THEN
    RAISE EXCEPTION 'Esperava 1 combinacao empresa/domicilio/banco/adquirente, encontrou %. Confira: empresa CNPJ 39303847000180, domicilio Santander (codigo 033) Ag 3346/Cc 13005859, adquirente "Rede%%".', ctx_count;
  END IF;
END $$;

INSERT INTO cs_releases_bank (
  id, company_id, banking_domicile_id, bank_id, acquirer_id, flag_id,
  release_date, release_value, release_category, release_category_code,
  modality_payment_bank, reconciliation_status, number_reconciliations,
  description_historical_bank
)
SELECT
  gen_random_uuid(),
  ctx.company_id,
  ctx.banking_domicile_id,
  ctx.bank_id,
  ctx.acquirer_id,
  f.id,
  t.release_date,
  t.release_value,
  3, -- ReleaseCategoryEnum.RECEIPT
  3,
  t.modality_payment_bank,
  1, -- StatusPaymentBankEnum.PENDING
  0,
  t.description
FROM tmp_santander_releases t
CROSS JOIN tmp_ctx ctx
JOIN cs_flag f ON f.name = t.flag_name
WHERE NOT EXISTS (
  SELECT 1 FROM cs_releases_bank rb
  WHERE rb.banking_domicile_id = ctx.banking_domicile_id
    AND rb.release_date = t.release_date
    AND rb.release_value = t.release_value
    AND rb.modality_payment_bank IS NOT DISTINCT FROM t.modality_payment_bank
    AND rb.acquirer_id IS NOT DISTINCT FROM ctx.acquirer_id
    AND rb.flag_id IS NOT DISTINCT FROM f.id
    AND rb.establishment_id IS NULL
);

-- Diagnóstico
SELECT count(*) AS total_linhas_no_lote FROM tmp_santander_releases;

SELECT count(*) AS ja_existentes_puladas
FROM tmp_santander_releases t
CROSS JOIN tmp_ctx ctx
JOIN cs_flag f ON f.name = t.flag_name
WHERE EXISTS (
  SELECT 1 FROM cs_releases_bank rb
  WHERE rb.banking_domicile_id = ctx.banking_domicile_id
    AND rb.release_date = t.release_date
    AND rb.release_value = t.release_value
    AND rb.modality_payment_bank IS NOT DISTINCT FROM t.modality_payment_bank
    AND rb.acquirer_id IS NOT DISTINCT FROM ctx.acquirer_id
    AND rb.flag_id IS NOT DISTINCT FROM f.id
    AND rb.establishment_id IS NULL
);

COMMIT;
