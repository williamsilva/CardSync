ALTER TABLE cs_adjustment
  ADD COLUMN ecommerce BIT(1) NULL,
  ADD COLUMN pv_number INT NULL,
  ADD COLUMN installment_number INT NULL,
  ADD COLUMN installment_total INT NULL,
  ADD COLUMN adjustment_sequence INT NULL,
  ADD COLUMN raw_adjustment_code VARCHAR(80) NULL,
  ADD COLUMN source_record_identifier VARCHAR(20) NULL,
  ADD COLUMN release_date DATE NULL,
  ADD COLUMN original_due_date DATE NULL,
  ADD COLUMN gross_value DECIMAL(18,8) NULL,
  ADD COLUMN liquid_value DECIMAL(18,8) NULL,
  ADD COLUMN discount_value DECIMAL(18,8) NULL,
  ADD COLUMN establishment_id BINARY(16) NULL,
  ADD COLUMN processed_file_id BINARY(16) NULL;

ALTER TABLE cs_adjustment
  ADD CONSTRAINT fk_cs_adjustment_establishment FOREIGN KEY (establishment_id) REFERENCES cs_establishment(id) ON UPDATE CASCADE,
  ADD CONSTRAINT fk_cs_adjustment_processed_file FOREIGN KEY (processed_file_id) REFERENCES cs_processed_file(id) ON UPDATE CASCADE;

CREATE INDEX idx_cs_adjustment_processed_file ON cs_adjustment(processed_file_id);
CREATE INDEX idx_cs_adjustment_record_type ON cs_adjustment(record_type, source_record_identifier);
CREATE INDEX idx_cs_adjustment_context ON cs_adjustment(company_id, acquirer_id, establishment_id, rv_flag_adjustment_id);
CREATE INDEX idx_cs_adjustment_rv_original ON cs_adjustment(rv_number_original, pv_number_original, rv_date_original);
