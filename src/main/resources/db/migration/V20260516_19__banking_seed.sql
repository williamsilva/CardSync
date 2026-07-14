INSERT INTO cs_bank(id, name, code, active, created_at, updated_at, created_by_id) VALUES
    (UUID_TO_BIN(UUID()),'Banco Do Brasil', '001', 0, NOW(), NOW(),NULL),
    (UUID_TO_BIN(UUID()),'Banrisul', '41', 0, NOW(), NOW(),NULL),
    (UUID_TO_BIN(UUID()),'Bradesco', '237', 1, NOW(), NOW(),NULL),
    (UUID_TO_BIN(UUID()),'Caixa Econômica Federal', '104', 0, NOW(), NOW(),NULL),
    (UUID_TO_BIN(UUID()),'HSBC', '399', 0, NOW(), NOW(),NULL),
    (UUID_TO_BIN(UUID()),'Itaú', '341', 1, NOW(), NOW(),NULL),
    (UUID_TO_BIN(UUID()),'Safra', '422', 0, NOW(), NOW(),NULL),
    (UUID_TO_BIN(UUID()),'Santander', '033', 1, NOW(), NOW(),NULL),
    (UUID_TO_BIN(UUID()),'Sicredi', '748', 1, NOW(), NOW(),NULL);

  INSERT INTO cs_banking_domicile(id, agency, current_account, account_digit, bank_id, company_id, created_at, updated_at, created_by_id) VALUES
    (UUID_TO_BIN(UUID()),0701, 21490, 0, (SELECT id FROM cs_bank WHERE code = '341'), (SELECT id FROM cs_company WHERE cnpj = '28499334000170'),
		NOW(), NOW(),NULL),
    (UUID_TO_BIN(UUID()),8639, 24515, 1, (SELECT id FROM cs_bank WHERE code = '341'), (SELECT id FROM cs_company WHERE cnpj = '36033801000109'),
		NOW(), NOW(),NULL),
    (UUID_TO_BIN(UUID()),8639, 40339, 6, (SELECT id FROM cs_bank WHERE code = '341'), (SELECT id FROM cs_company WHERE cnpj = '36033801000109'),
		NOW(), NOW(),NULL),
    (UUID_TO_BIN(UUID()),8639, 43118, 1, (SELECT id FROM cs_bank WHERE code = '341'), (SELECT id FROM cs_company WHERE cnpj = '36033801000109'),
		NOW(), NOW(),NULL),
    (UUID_TO_BIN(UUID()),1200, 58891, 1, (SELECT id FROM cs_bank WHERE code = '237'), (SELECT id FROM cs_company WHERE cnpj = '36033801000109'),
		NOW(), NOW(),NULL),
    (UUID_TO_BIN(UUID()),3346, 13005467, 0, (SELECT id FROM cs_bank WHERE code = '033'), (SELECT id FROM cs_company WHERE cnpj = '28499334000170'),
		NOW(), NOW(),NULL),
    (UUID_TO_BIN(UUID()),3346, 13005859, 5, (SELECT id FROM cs_bank WHERE code = '033'), (SELECT id FROM cs_company WHERE cnpj = '39303847000180'),
        NOW(), NOW(),NULL);