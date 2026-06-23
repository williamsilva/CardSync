ALTER TABLE cs_establishment
  ADD COLUMN opening_date DATE NULL AFTER pv_number,
  ADD COLUMN closing_date DATE NULL AFTER opening_date;
