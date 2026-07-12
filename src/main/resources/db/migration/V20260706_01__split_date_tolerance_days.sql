ALTER TABLE cs_reconciliation_settings
    ADD COLUMN date_tolerance_days_before INT NOT NULL DEFAULT 5,
    ADD COLUMN date_tolerance_days_after  INT NOT NULL DEFAULT 3;

UPDATE cs_reconciliation_settings
SET date_tolerance_days_before = date_tolerance_days,
    date_tolerance_days_after  = date_tolerance_days;

ALTER TABLE cs_reconciliation_settings
    DROP COLUMN date_tolerance_days;
