INSERT INTO cs_company(id, status, type, fantasy_name, social_reason, cnpj, created_at, updated_at, created_by_id) VALUES
    (gen_random_uuid(), 1, 1, 'Acquamania Multiplo Lazer S.A', 'Acquamania', '39303847000180', NOW(), NOW(),
        NULL),
    (gen_random_uuid(), 1, 1, 'Clam Qualidade de Vida', 'Clam Qualidade', '36033801000109', NOW(), NOW(),
        NULL),
    (gen_random_uuid(), 1, 1, 'Mac Serviços e Convêniencia LTDA', 'Mac Serviços', '28499334000170', NOW(), NOW(),
        NULL);

INSERT INTO cs_acquirer(id, status, cnpj, fantasy_name, social_reason, file_identifier, created_at, updated_at, created_by_id) VALUES
    (gen_random_uuid(), 1, '01425787000104', 'Rede S/A', 'Rede', 'Rede', NOW(), NOW(),
     NULL),
    (gen_random_uuid(), 2, '47848271000165', 'Cielo S/A', 'Cielo', 'Cielo', NOW(), NOW(),
     NULL),
    (gen_random_uuid(), 2, '22222222222222', 'SafraPay', 'SafraPay', 'SafraPay', NOW(), NOW(),
     NULL),
    (gen_random_uuid(), 2, '11111111111111', 'Outra', 'Outra Adquirente', 'Outra', NOW(), NOW(),
     NULL),
    (gen_random_uuid(), 2, '33333333333333', 'Sicredi', 'Sicredi', 'Sicredi', NOW(), NOW(),
     NULL);
