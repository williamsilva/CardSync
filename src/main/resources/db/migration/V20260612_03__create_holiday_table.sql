CREATE TABLE cs_holiday (
  id UUID NOT NULL,
  holiday_date DATE NOT NULL,
  name VARCHAR(150) NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NULL,
  created_by_id UUID NULL,
  updated_by_id UUID NULL,
  PRIMARY KEY (id),
  CONSTRAINT uk_cs_holiday_date UNIQUE (holiday_date)
);

CREATE INDEX idx_cs_holiday_active_date ON cs_holiday (active, holiday_date);
