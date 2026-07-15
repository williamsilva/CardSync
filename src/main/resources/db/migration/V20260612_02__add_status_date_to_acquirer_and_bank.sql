ALTER TABLE cs_bank ADD COLUMN status_date TIMESTAMP(6) NULL;
ALTER TABLE cs_acquirer ADD COLUMN status_date TIMESTAMP(6) NULL;

UPDATE cs_bank SET status_date = COALESCE(updated_at, created_at, CURRENT_TIMESTAMP(6)) WHERE status_date IS NULL;
UPDATE cs_acquirer SET status_date = COALESCE(updated_at, created_at, CURRENT_TIMESTAMP(6)) WHERE status_date IS NULL;
