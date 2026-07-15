ALTER TABLE cs_holiday
  DROP CONSTRAINT uk_cs_holiday_date,
  ADD COLUMN recurring BOOLEAN NOT NULL DEFAULT FALSE;

-- Nova constraint: (recurring, holiday_date) — permite coexistir recorrente 1900-12-25 e específico 2025-12-25
ALTER TABLE cs_holiday
  ADD CONSTRAINT uk_cs_holiday_recurring_date UNIQUE (recurring, holiday_date);

-- Feriados nacionais fixos do Brasil (ano 1900 = recorrente)
INSERT INTO cs_holiday (id, holiday_date, name, recurring, status, created_at) VALUES
  (gen_random_uuid(), '1900-01-01', 'Confraternização Universal', TRUE, 1, NOW()),
  (gen_random_uuid(), '1900-04-21', 'Tiradentes',                  TRUE, 1, NOW()),
  (gen_random_uuid(), '1900-05-01', 'Dia do Trabalho',             TRUE, 1, NOW()),
  (gen_random_uuid(), '1900-09-07', 'Independência do Brasil',     TRUE, 1, NOW()),
  (gen_random_uuid(), '1900-10-12', 'Nossa Senhora Aparecida',     TRUE, 1, NOW()),
  (gen_random_uuid(), '1900-11-02', 'Finados',                     TRUE, 1, NOW()),
  (gen_random_uuid(), '1900-11-15', 'Proclamação da República',    TRUE, 1, NOW()),
  (gen_random_uuid(), '1900-11-20', 'Dia Nacional de Zumbi e da Consciência Negra', TRUE, 1, NOW()),
  (gen_random_uuid(), '1900-12-25', 'Natal',                       TRUE, 1, NOW()),
  (gen_random_uuid(), '2025-03-03', 'Carnaval (Ponto Facultativo)', FALSE, 1, NOW()),
  (gen_random_uuid(), '2025-03-04', 'Carnaval (Ponto Facultativo)', FALSE, 1, NOW()),
  (gen_random_uuid(), '2025-04-18', 'Sexta-Feira Santa (Paixão de Cristo)', FALSE, 1, NOW()),
  (gen_random_uuid(), '2025-06-19', 'Corpus Christi (Ponto Facultativo)', FALSE, 1, NOW()),
  (gen_random_uuid(), '2026-02-16', 'Carnaval (Ponto Facultativo)', FALSE, 1, NOW()),
  (gen_random_uuid(), '2026-02-17', 'Carnaval (Ponto Facultativo)', FALSE, 1, NOW()),
  (gen_random_uuid(), '2026-04-03', 'Paixão de Cristo (Sexta-feira Santa)', FALSE, 1, NOW()),
  (gen_random_uuid(), '2026-06-04', 'Corpus Christi',                       FALSE, 1, NOW());
