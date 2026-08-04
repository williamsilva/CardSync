/*
 * Cadastra contratos de taxa Cielo por PV Number para as 3 empresas/estabelecimentos que usam
 * essa adquirente (fonte: tabela de taxas informada pelo usuário, portal da Cielo).
 *
 * Vigência: 2024-01-01 até 2026-12-31 (mesmo período usado no contrato Rede, V20260523_02).
 *
 * Modelo idêntico ao adotado pra Rede:
 * - 1 contrato por empresa + adquirente Cielo + estabelecimento/PV;
 * - rate/rate_ecommerce recebem a mesma taxa (não foi informada taxa e-commerce distinta);
 * - payment_term_days/payment_term_days_ecommerce: D+1 débito, D+31 crédito (à vista e
 *   parcelado) — mesmo padrão já usado no contrato Rede, confirmado pelo usuário;
 * - percentuais em formato percentual, ex.: 0.79 = 0,79%;
 * - installment_min/installment_max diferenciam sub-faixas DENTRO de uma modalidade quando a
 *   taxa real varia (só a Banescard: 3,20% em 2-3x vs 3,30% em 4-6x — as demais bandeiras são
 *   uniformes em toda a faixa 2-6x/7-12x). Isso só é seguro porque TransactionAcqEntity.installment
 *   agora guarda o TOTAL de parcelas da venda (ver fix "Fix Cielo TransactionAcqEntity.installment:
 *   total, not current parcela", mesmo dia) — antes desse fix, sub-faixas finas dentro da mesma
 *   modalidade dariam resultado errado pra Cielo (installment guardava a parcela ATUAL, não o total).
 *
 * Bandeiras de fora (documentado, não é omissão silenciosa):
 * - Agiplan: aparece na tabela pública da Cielo mas não tem cs_flag/cs_flag_acquirer cadastrado
 *   pra Cielo (ver V20260804_02) e 100% das vendas reais desses PVs já resolvem bandeira sem
 *   usá-la — não é usada por este cliente, fica de fora.
 * - American Express não oferece parcelamento 7-12x (tabela mostra "-"); Hipercard não oferece
 *   débito nem parcelamento 7-12x. Sorocred/Banescard/Cabal não oferecem débito exceto Banescard/Cabal.
 *   Todas as combinações realmente ausentes na tabela ficam sem linha (sem contrato = sem taxa
 *   aplicável, não taxa zero).
 *
 * Modalidades (mesmo código do contrato Rede):
 * 1 = CASH_DEBIT, 2 = CASH_CREDIT, 3 = INSTALLMENT_CREDIT_2_6, 4 = INSTALLMENT_CREDIT_7_12,
 * 8 = DIGITAL_WALLET (Pix Cielo, taxa fixa 0,97%).
 */

DROP TABLE IF EXISTS tmp_cielo_contract_seed_establishments;
CREATE TEMPORARY TABLE tmp_cielo_contract_seed_establishments (
  cnpj VARCHAR(14) NOT NULL,
  pv_number BIGINT NOT NULL,
  description VARCHAR(150) NOT NULL,
  PRIMARY KEY (cnpj, pv_number)
);

INSERT INTO tmp_cielo_contract_seed_establishments (cnpj, pv_number, description) VALUES
  ('36033801000109', 1051583117, 'Clam Qualidade de Vida - Cielo S/A - PV 1051583117'),
  ('36033801000109', 1018802468, 'Clam Qualidade de Vida - Cielo S/A - PV 1018802468'),
  ('28499334000170', 1100125202, 'Mac Serviços e Conveniência LTDA - Cielo S/A - PV 1100125202');

DROP TABLE IF EXISTS tmp_cielo_contract_seed_rates;
CREATE TEMPORARY TABLE tmp_cielo_contract_seed_rates (
  flag_name VARCHAR(50) NOT NULL,
  modality INT NOT NULL,
  installment_min INT NULL,
  installment_max INT NULL,
  rate DECIMAL(18,8) NOT NULL,
  payment_term_days INT NOT NULL
);

INSERT INTO tmp_cielo_contract_seed_rates
  (flag_name, modality, installment_min, installment_max, rate, payment_term_days)
VALUES
  -- Débito
  ('Visa', 1, NULL, NULL, 0.79, 1),
  ('Mastercard', 1, NULL, NULL, 0.79, 1),
  ('Elo', 1, NULL, NULL, 1.99, 1),
  ('Banescard', 1, NULL, NULL, 0.00, 1),
  ('Cabal', 1, NULL, NULL, 2.15, 1),

  -- Crédito à vista
  ('Visa', 2, NULL, NULL, 2.48, 31),
  ('Mastercard', 2, NULL, NULL, 2.48, 31),
  ('Elo', 2, NULL, NULL, 3.37, 31),
  ('American Express', 2, NULL, NULL, 4.69, 31),
  ('Diners Club', 2, NULL, NULL, 3.05, 31),
  ('Hipercard', 2, NULL, NULL, 3.05, 31),
  ('Banescard', 2, NULL, NULL, 0.00, 31),
  ('Sorocred', 2, NULL, NULL, 2.90, 31),
  ('Cabal', 2, NULL, NULL, 2.90, 31),

  -- Parcelado loja 2x-6x
  ('Visa', 3, 2, 6, 3.15, 31),
  ('Mastercard', 3, 2, 6, 3.15, 31),
  ('Elo', 3, 2, 6, 3.37, 31),
  ('American Express', 3, 2, 6, 4.69, 31),
  ('Diners Club', 3, 2, 6, 3.91, 31),
  ('Hipercard', 3, 2, 6, 3.91, 31),
  ('Sorocred', 3, 2, 6, 3.70, 31),
  ('Cabal', 3, 2, 6, 3.70, 31),
  -- Banescard: única bandeira com taxa diferente dentro da faixa 2-6x (2-3x vs 4-6x)
  ('Banescard', 3, 2, 3, 3.20, 31),
  ('Banescard', 3, 4, 6, 3.30, 31),

  -- Parcelado loja 7x-12x (Amex e Hipercard não oferecem, ficam de fora)
  ('Visa', 4, 7, 12, 3.18, 31),
  ('Mastercard', 4, 7, 12, 3.18, 31),
  ('Elo', 4, 7, 12, 3.48, 31),
  ('Diners Club', 4, 7, 12, 3.91, 31),
  ('Banescard', 4, 7, 12, 4.00, 31),
  ('Sorocred', 4, 7, 12, 4.40, 31),
  ('Cabal', 4, 7, 12, 4.40, 31),

  -- Pix Cielo: taxa fixa, sem faixa de parcela
  ('Pix', 8, NULL, NULL, 0.97, 1);

INSERT INTO cs_contracts (
  id,
  status,
  start_date,
  end_date,
  description,
  company_id,
  acquirer_id,
  establishment_id,
  created_at,
  updated_at,
  created_by_id
)
SELECT
  gen_random_uuid(),
  1,
  '2024-01-01',
  '2026-12-31',
  seed.description,
  company.id,
  acquirer.id,
  establishment.id,
  CURRENT_TIMESTAMP(6),
  CURRENT_TIMESTAMP(6),
  NULL
FROM tmp_cielo_contract_seed_establishments seed
JOIN cs_company company
  ON company.cnpj = seed.cnpj
JOIN cs_acquirer acquirer
  ON acquirer.file_identifier = 'Cielo'
JOIN cs_establishment establishment
  ON establishment.company_id = company.id
 AND establishment.acquirer_id = acquirer.id
 AND establishment.pv_number = seed.pv_number
WHERE NOT EXISTS (
  SELECT 1
  FROM cs_contracts existing
  WHERE existing.establishment_id = establishment.id
    AND existing.description = seed.description
    AND existing.start_date = '2024-01-01'
    AND existing.end_date = '2026-12-31'
);

INSERT INTO cs_contract_flags (id, flag_id, contract_id)
SELECT
  gen_random_uuid(),
  flag.id,
  contract.id
FROM tmp_cielo_contract_seed_establishments seed
JOIN cs_company company
  ON company.cnpj = seed.cnpj
JOIN cs_acquirer acquirer
  ON acquirer.file_identifier = 'Cielo'
JOIN cs_establishment establishment
  ON establishment.company_id = company.id
 AND establishment.acquirer_id = acquirer.id
 AND establishment.pv_number = seed.pv_number
JOIN cs_contracts contract
  ON contract.company_id = company.id
 AND contract.acquirer_id = acquirer.id
 AND contract.establishment_id = establishment.id
 AND contract.description = seed.description
 AND contract.start_date = '2024-01-01'
 AND contract.end_date = '2026-12-31'
CROSS JOIN (
  SELECT DISTINCT flag_name
  FROM tmp_cielo_contract_seed_rates
) rate_flags
JOIN cs_flag flag
  ON flag.name = rate_flags.flag_name
WHERE NOT EXISTS (
  SELECT 1
  FROM cs_contract_flags existing
  WHERE existing.contract_id = contract.id
    AND existing.flag_id = flag.id
);

INSERT INTO cs_contract_rates (
  id,
  modality,
  rate,
  rate_ecommerce,
  payment_term_days,
  payment_term_days_ecommerce,
  installment_min,
  installment_max,
  contract_flag_id
)
SELECT
  gen_random_uuid(),
  seed_rate.modality,
  seed_rate.rate,
  seed_rate.rate,
  seed_rate.payment_term_days,
  seed_rate.payment_term_days,
  seed_rate.installment_min,
  seed_rate.installment_max,
  contract_flag.id
FROM tmp_cielo_contract_seed_establishments seed
JOIN cs_company company
  ON company.cnpj = seed.cnpj
JOIN cs_acquirer acquirer
  ON acquirer.file_identifier = 'Cielo'
JOIN cs_establishment establishment
  ON establishment.company_id = company.id
 AND establishment.acquirer_id = acquirer.id
 AND establishment.pv_number = seed.pv_number
JOIN cs_contracts contract
  ON contract.company_id = company.id
 AND contract.acquirer_id = acquirer.id
 AND contract.establishment_id = establishment.id
 AND contract.description = seed.description
 AND contract.start_date = '2024-01-01'
 AND contract.end_date = '2026-12-31'
CROSS JOIN tmp_cielo_contract_seed_rates seed_rate
JOIN cs_flag flag
  ON flag.name = seed_rate.flag_name
JOIN cs_contract_flags contract_flag
  ON contract_flag.contract_id = contract.id
 AND contract_flag.flag_id = flag.id
WHERE NOT EXISTS (
  SELECT 1
  FROM cs_contract_rates existing
  WHERE existing.contract_flag_id = contract_flag.id
    AND existing.modality = seed_rate.modality
    AND existing.installment_min IS NOT DISTINCT FROM seed_rate.installment_min
    AND existing.installment_max IS NOT DISTINCT FROM seed_rate.installment_max
);

DROP TABLE IF EXISTS tmp_cielo_contract_seed_rates;
DROP TABLE IF EXISTS tmp_cielo_contract_seed_establishments;
