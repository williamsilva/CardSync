ALTER TABLE cs_holiday
  ADD COLUMN status INT NOT NULL DEFAULT 1,
  ADD COLUMN status_date TIMESTAMP(6) NULL;

UPDATE cs_holiday
SET status = CASE
  WHEN active = TRUE THEN 1
  ELSE 2
END,
status_date = COALESCE(updated_at, created_at);

DROP INDEX IF EXISTS idx_cs_holiday_active_date;

ALTER TABLE cs_holiday
  DROP COLUMN active;

CREATE INDEX idx_cs_holiday_status_date
  ON cs_holiday (status, holiday_date);
