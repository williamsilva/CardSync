ALTER TABLE cs_establishment
  ADD COLUMN opening_date DATE NULL AFTER pv_number,
  ADD COLUMN closing_date DATE NULL AFTER opening_date;

UPDATE cs_establishment
    SET opening_date = DATE('2024-07-01')
    WHERE opening_date IS NULL;