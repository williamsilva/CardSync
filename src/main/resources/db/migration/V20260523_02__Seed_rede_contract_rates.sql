/*
 * Cadastra contratos de taxa Rede por PV Number para as 3 empresas baseadas na planilha "Taxas rede.xlsx".
 *
 * Vigência: 2024-01-01 até 2026-12-31.
 *
 * Modelo adotado:
 * - 1 contrato por empresa + adquirente Rede + estabelecimento/PV;
 * - contratos antigos genéricos por empresa/Rede com establishment_id NULL, criados pela seed anterior,
 *   são inativados e encerrados em 2023-12-31 para evitar concorrência com os contratos por PV;
 * - rate/rate_ecommerce recebem a mesma taxa da planilha;
 * - payment_term_days/payment_term_days_ecommerce recebem o prazo numérico da planilha;
 * - percentuais gravados em formato percentual, ex.: 0.97 = 0,97%.
 *
 * Modalidades:
 * 1 = CASH_DEBIT
 * 2 = CASH_CREDIT
 * 3 = INSTALLMENT_CREDIT_2_6
 * 4 = INSTALLMENT_CREDIT_7_12
 * 5 = INSTALLMENT_CREDIT_13_21
 */

DROP TEMPORARY TABLE IF EXISTS tmp_rede_contract_seed_establishments;
CREATE TEMPORARY TABLE tmp_rede_contract_seed_establishments (
  cnpj VARCHAR(14) NOT NULL,
  pv_number BIGINT NOT NULL,
  description VARCHAR(150) NOT NULL,
  PRIMARY KEY (cnpj, pv_number)
) ENGINE=Memory;

INSERT INTO tmp_rede_contract_seed_establishments (cnpj, pv_number, description) VALUES
  ('39303847000180', 7867379,  'Acquamania Multiplo Lazer S.A - Rede S/A - PV 7867379'),
  ('39303847000180', 93693702, 'Acquamania Multiplo Lazer S.A - Rede S/A - PV 93693702'),
  ('36033801000109', 7866470,  'Clam Qualidade de Vida - Rede S/A - PV 7866470'),
  ('36033801000109', 88033759, 'Clam Qualidade de Vida - Rede S/A - PV 88033759'),
  ('28499334000170', 74705318, 'Mac Serviços e Conveniência LTDA - Rede S/A - PV 74705318'),
  ('28499334000170', 78589126, 'Mac Serviços e Conveniência LTDA - Rede S/A - PV 78589126');

DROP TEMPORARY TABLE IF EXISTS tmp_rede_contract_seed_rates;
CREATE TEMPORARY TABLE tmp_rede_contract_seed_rates (
  flag_name VARCHAR(50) NOT NULL,
  modality INT NOT NULL,
  installment_min INT NULL,
  installment_max INT NULL,
  rate DECIMAL(18,8) NOT NULL,
  payment_term_days INT NOT NULL
) ENGINE=Memory;

INSERT INTO tmp_rede_contract_seed_rates
  (flag_name, modality, installment_min, installment_max, rate, payment_term_days)
VALUES
  ('Mastercard', 1, NULL, NULL, 0.97, 1),
  ('Visa', 1, NULL, NULL, 0.97, 1),
  ('Cabal', 1, NULL, NULL, 1.93, 1),
  ('Sorocred', 1, NULL, NULL, 1.93, 1),
  ('JCB', 1, NULL, NULL, 1.93, 1),
  ('Elo', 1, NULL, NULL, 1.93, 1),

  ('Mastercard', 2, 1, 1, 2.34, 31),
  ('Visa', 2, 1, 1, 2.34, 31),
  ('Diners Club', 2, 1, 1, 3.44, 31),
  ('Cabal', 2, 1, 1, 3.44, 31),
  ('Sorocred', 2, 1, 1, 3.44, 31),
  ('Banescard', 2, 1, 1, 3.44, 31),
  ('JCB', 2, 1, 1, 3.44, 31),
  ('Credz', 2, 1, 1, 3.44, 31),
  ('American Express', 2, 1, 1, 3.44, 31),
  ('Elo', 2, 1, 1, 3.44, 31),

  ('Mastercard', 3, 2, 6, 2.24, 31),
  ('Visa', 3, 2, 6, 2.24, 31),
  ('Diners Club', 3, 2, 6, 4.35, 31),
  ('Cabal', 3, 2, 6, 4.35, 31),
  ('Sorocred', 3, 2, 6, 4.35, 31),
  ('Banescard', 3, 2, 6, 4.35, 31),
  ('JCB', 3, 2, 6, 4.35, 31),
  ('Credz', 3, 2, 6, 4.35, 31),
  ('American Express', 3, 2, 6, 4.35, 31),
  ('Elo', 3, 2, 6, 4.35, 31),

  ('Mastercard', 4, 7, 12, 2.60, 31),
  ('Visa', 4, 7, 12, 2.60, 31),
  ('Diners Club', 4, 7, 12, 4.36, 31),
  ('Cabal', 4, 7, 12, 4.36, 31),
  ('Sorocred', 4, 7, 12, 4.36, 31),
  ('Banescard', 4, 7, 12, 4.36, 31),
  ('JCB', 4, 7, 12, 4.36, 31),
  ('Credz', 4, 7, 12, 4.36, 31),
  ('American Express', 4, 7, 12, 4.36, 31),
  ('Elo', 4, 7, 12, 4.36, 31),

  ('Mastercard', 5, 13, 21, 2.60, 31),
  ('Visa', 5, 13, 21, 2.60, 31),
  ('Elo', 5, 13, 21, 4.36, 31);


-- Corrige bancos antigos onde cs_flag_company foi criada com índice único apenas em flag_id.
-- O modelo correto é permitir a mesma bandeira em várias empresas, bloqueando apenas duplicidade do par flag_id + company_id.
DROP PROCEDURE IF EXISTS cs_create_unique_index_if_missing;

DELIMITER $$

CREATE PROCEDURE cs_create_unique_index_if_missing(
    IN p_table_name VARCHAR(128),
    IN p_index_name VARCHAR(128),
    IN p_index_columns TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
          FROM information_schema.statistics
         WHERE table_schema = DATABASE()
           AND table_name = p_table_name
           AND index_name = p_index_name
    ) THEN
        SET @sql = CONCAT(
            'CREATE UNIQUE INDEX `',
            REPLACE(p_index_name, '`', '``'),
            '` ON `',
            REPLACE(p_table_name, '`', '``'),
            '` (',
            p_index_columns,
            ')'
        );

        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

DELIMITER ;

DROP PROCEDURE IF EXISTS cs_drop_index_if_exists;

DELIMITER $$

CREATE PROCEDURE cs_drop_index_if_exists(
    IN p_table_name VARCHAR(128),
    IN p_index_name VARCHAR(128)
)
BEGIN
    IF EXISTS (
        SELECT 1
          FROM information_schema.statistics
         WHERE table_schema = DATABASE()
           AND table_name = p_table_name
           AND index_name = p_index_name
    ) THEN
        SET @sql = CONCAT(
            'DROP INDEX `',
            REPLACE(p_index_name, '`', '``'),
            '` ON `',
            REPLACE(p_table_name, '`', '``'),
            '`'
        );

        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

DELIMITER ;

CALL cs_create_unique_index_if_missing(
  'cs_flag_company',
  'uk_cs_flag_company_flag_company',
  '`flag_id`, `company_id`'
);

CALL cs_drop_index_if_exists('cs_flag_company', 'flag_id');

DROP PROCEDURE IF EXISTS cs_drop_index_if_exists;
DROP PROCEDURE IF EXISTS cs_create_unique_index_if_missing;

-- Encerra/inativa contratos genéricos por empresa + Rede da seed anterior, para não concorrerem com os contratos por PV.
UPDATE cs_contracts contract
JOIN cs_acquirer acquirer
  ON acquirer.id = contract.acquirer_id
SET
  contract.status = 2,
  contract.end_date = '2023-12-31',
  contract.updated_at = CURRENT_TIMESTAMP(6)
WHERE acquirer.cnpj = '01425787000104'
  AND contract.establishment_id IS NULL
  AND contract.start_date = '2025-01-01'
  AND contract.description LIKE '%Rede S/A - Taxas padrão%';

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
  UUID_TO_BIN(UUID()),
  1,
  '2024-01-01',
  '2026-12-31',
  seed.description,
  company.id,
  acquirer.id,
  establishment.id,
  CURRENT_TIMESTAMP(6),
  CURRENT_TIMESTAMP(6),
  user_created.id
FROM tmp_rede_contract_seed_establishments seed
JOIN cs_company company
  ON company.cnpj = seed.cnpj
JOIN cs_acquirer acquirer
  ON acquirer.cnpj = '01425787000104'
JOIN cs_establishment establishment
  ON establishment.company_id = company.id
 AND establishment.acquirer_id = acquirer.id
 AND establishment.pv_number = seed.pv_number
LEFT JOIN cs_users user_created
  ON user_created.user_name = 'suporte@cardsync.com.br'
WHERE NOT EXISTS (
  SELECT 1
  FROM cs_contracts existing
  WHERE existing.establishment_id = establishment.id
    AND existing.description = seed.description
    AND existing.start_date = '2024-01-01'
    AND existing.end_date = '2026-12-31'
);

-- Não inserimos em cs_flag_company nesta seed.
-- A relação necessária para o contrato é cs_contract_flags + cs_contract_rates.
-- Em alguns bancos antigos, cs_flag_company pode ter índice único apenas em flag_id,
-- o que impede a mesma bandeira em mais de uma empresa e quebrava a migration.


INSERT INTO cs_contract_flags (id, flag_id, contract_id)
SELECT
  UUID_TO_BIN(UUID()),
  flag.id,
  contract.id
FROM tmp_rede_contract_seed_establishments seed
JOIN cs_company company
  ON company.cnpj = seed.cnpj
JOIN cs_acquirer acquirer
  ON acquirer.cnpj = '01425787000104'
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
  FROM tmp_rede_contract_seed_rates
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
  UUID_TO_BIN(UUID()),
  seed_rate.modality,
  seed_rate.rate,
  seed_rate.rate,
  seed_rate.payment_term_days,
  seed_rate.payment_term_days,
  seed_rate.installment_min,
  seed_rate.installment_max,
  contract_flag.id
FROM tmp_rede_contract_seed_establishments seed
JOIN cs_company company
  ON company.cnpj = seed.cnpj
JOIN cs_acquirer acquirer
  ON acquirer.cnpj = '01425787000104'
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
CROSS JOIN tmp_rede_contract_seed_rates seed_rate
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
    AND existing.installment_min <=> seed_rate.installment_min
    AND existing.installment_max <=> seed_rate.installment_max
);

DROP TEMPORARY TABLE IF EXISTS tmp_rede_contract_seed_rates;
DROP TEMPORARY TABLE IF EXISTS tmp_rede_contract_seed_establishments;
