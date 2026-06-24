ALTER TABLE cs_holiday
  DROP INDEX uk_cs_holiday_date,
  ADD COLUMN recurring BIT(1) NOT NULL DEFAULT b'0' AFTER name;

-- Nova constraint: (recurring, holiday_date) — permite coexistir recorrente 1900-12-25 e específico 2025-12-25
ALTER TABLE cs_holiday
  ADD CONSTRAINT uk_cs_holiday_recurring_date UNIQUE (recurring, holiday_date);

-- Feriados nacionais fixos do Brasil (ano 1900 = recorrente)
INSERT INTO cs_holiday (id, holiday_date, name, recurring, status, created_at) VALUES
  (UUID_TO_BIN(UUID()), '1900-01-01', 'Confraternização Universal', b'1', 1, NOW()),
  (UUID_TO_BIN(UUID()), '1900-04-21', 'Tiradentes',                  b'1', 1, NOW()),
  (UUID_TO_BIN(UUID()), '1900-05-01', 'Dia do Trabalho',             b'1', 1, NOW()),
  (UUID_TO_BIN(UUID()), '1900-09-07', 'Independência do Brasil',     b'1', 1, NOW()),
  (UUID_TO_BIN(UUID()), '1900-10-12', 'Nossa Senhora Aparecida',     b'1', 1, NOW()),
  (UUID_TO_BIN(UUID()), '1900-11-02', 'Finados',                     b'1', 1, NOW()),
  (UUID_TO_BIN(UUID()), '1900-11-15', 'Proclamação da República',    b'1', 1, NOW()),
  (UUID_TO_BIN(UUID()), '1900-11-20', 'Dia Nacional de Zumbi e da Consciência Negra', b'1', 1, NOW()),
  (UUID_TO_BIN(UUID()), '1900-12-25', 'Natal',                       b'1', 1, NOW()),
  (UUID_TO_BIN(UUID()), '2025-03-03', 'Carnaval (Ponto Facultativo)', b'0', 1, NOW()),
  (UUID_TO_BIN(UUID()), '2025-03-04', 'Carnaval (Ponto Facultativo)', b'0', 1, NOW()),
  (UUID_TO_BIN(UUID()), '2025-04-18', 'Sexta-Feira Santa (Paixão de Cristo)', b'0', 1, NOW()),
  (UUID_TO_BIN(UUID()), '2025-06-19', 'Corpus Christi (Ponto Facultativo)', b'0', 1, NOW()),
  (UUID_TO_BIN(UUID()), '2026-02-16', 'Carnaval (Ponto Facultativo)', b'0', 1, NOW()),
  (UUID_TO_BIN(UUID()), '2026-02-17', 'Carnaval (Ponto Facultativo)', b'0', 1, NOW()),
  (UUID_TO_BIN(UUID()), '2026-04-03', 'Paixão de Cristo (Sexta-feira Santa)', b'0', 1, NOW()),
  (UUID_TO_BIN(UUID()), '2026-06-04', 'Corpus Christi',                       b'0', 1, NOW());
