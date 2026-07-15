ALTER TABLE cs_establishment
  ADD COLUMN opening_date DATE NULL,
  ADD COLUMN closing_date DATE NULL;

UPDATE cs_establishment
    SET opening_date = DATE('2024-07-01')
    WHERE opening_date IS NULL;