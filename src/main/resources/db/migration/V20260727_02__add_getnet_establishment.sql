INSERT INTO cs_establishment (id, acquirer_id, type, pv_number, status, company_id, created_at, updated_at, created_by_id) VALUES
    (gen_random_uuid(), (SELECT id FROM cs_acquirer WHERE cnpj = '44444444444444'), 1, 350834, 2, (SELECT id FROM cs_company WHERE cnpj = '28499334000170'),
         NOW(), NOW(), NULL);