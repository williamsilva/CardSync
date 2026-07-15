INSERT INTO cs_bank(id, name, code, active, created_at, updated_at, created_by_id) VALUES
    (gen_random_uuid(),'Banco Do Brasil', '001', FALSE, NOW(), NOW(),NULL),
    (gen_random_uuid(),'Banrisul', '41', FALSE, NOW(), NOW(),NULL),
    (gen_random_uuid(),'Bradesco', '237', TRUE, NOW(), NOW(),NULL),
    (gen_random_uuid(),'Caixa Econômica Federal', '104', FALSE, NOW(), NOW(),NULL),
    (gen_random_uuid(),'HSBC', '399', FALSE, NOW(), NOW(),NULL),
    (gen_random_uuid(),'Itaú', '341', TRUE, NOW(), NOW(),NULL),
    (gen_random_uuid(),'Safra', '422', FALSE, NOW(), NOW(),NULL),
    (gen_random_uuid(),'Santander', '033', TRUE, NOW(), NOW(),NULL),
    (gen_random_uuid(),'Sicredi', '748', TRUE, NOW(), NOW(),NULL);

  INSERT INTO cs_banking_domicile(id, agency, current_account, account_digit, bank_id, company_id, created_at, updated_at, created_by_id) VALUES
    (gen_random_uuid(),0701, 21490, 0, (SELECT id FROM cs_bank WHERE code = '341'), (SELECT id FROM cs_company WHERE cnpj = '28499334000170'),
		NOW(), NOW(),NULL),
    (gen_random_uuid(),8639, 24515, 1, (SELECT id FROM cs_bank WHERE code = '341'), (SELECT id FROM cs_company WHERE cnpj = '36033801000109'),
		NOW(), NOW(),NULL),
    (gen_random_uuid(),8639, 40339, 6, (SELECT id FROM cs_bank WHERE code = '341'), (SELECT id FROM cs_company WHERE cnpj = '36033801000109'),
		NOW(), NOW(),NULL),
    (gen_random_uuid(),8639, 43118, 1, (SELECT id FROM cs_bank WHERE code = '341'), (SELECT id FROM cs_company WHERE cnpj = '36033801000109'),
		NOW(), NOW(),NULL),
    (gen_random_uuid(),1200, 58891, 1, (SELECT id FROM cs_bank WHERE code = '237'), (SELECT id FROM cs_company WHERE cnpj = '36033801000109'),
		NOW(), NOW(),NULL),
    (gen_random_uuid(),3346, 13005467, 0, (SELECT id FROM cs_bank WHERE code = '033'), (SELECT id FROM cs_company WHERE cnpj = '28499334000170'),
		NOW(), NOW(),NULL),
    (gen_random_uuid(),3346, 13005859, 5, (SELECT id FROM cs_bank WHERE code = '033'), (SELECT id FROM cs_company WHERE cnpj = '39303847000180'),
        NOW(), NOW(),NULL);