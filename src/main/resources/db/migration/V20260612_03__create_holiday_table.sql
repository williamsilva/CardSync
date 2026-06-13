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
  CONSTRAINT uk_cs_holiday_date UNIQUE (holiday_date),
  CONSTRAINT fk_cs_holiday_created_by FOREIGN KEY (created_by_id) REFERENCES cs_users (id),
  CONSTRAINT fk_cs_holiday_updated_by FOREIGN KEY (updated_by_id) REFERENCES cs_users (id)
);

CREATE INDEX idx_cs_holiday_active_date ON cs_holiday (active, holiday_date);
