CREATE TABLE cs_holiday (
  id BINARY(16) NOT NULL,
  holiday_date DATE NOT NULL,
  name VARCHAR(150) NOT NULL,
  active BIT(1) NOT NULL DEFAULT b'1',
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NULL,
  created_by_id BINARY(16) NULL,
  updated_by_id BINARY(16) NULL,
  PRIMARY KEY (id),
  CONSTRAINT uk_cs_holiday_date UNIQUE (holiday_date)
);

CREATE INDEX idx_cs_holiday_active_date ON cs_holiday (active, holiday_date);
