ALTER TABLE cs_holiday
  ADD COLUMN status INT NOT NULL DEFAULT 1 AFTER name,
  ADD COLUMN status_date DATETIME(6) NULL AFTER status;

UPDATE cs_holiday
SET status = CASE
  WHEN active = TRUE THEN 1
  ELSE 2
END,
status_date = COALESCE(updated_at, created_at);

ALTER TABLE cs_holiday
  DROP INDEX idx_cs_holiday_active_date,
  DROP COLUMN active;

CREATE INDEX idx_cs_holiday_status_date
  ON cs_holiday (status, holiday_date);
