
ALTER TABLE cs_totalizer_matrix
  ADD COLUMN total_gross_value DECIMAL(18,8) NULL,
  ADD COLUMN rejected_cv_nsu_quantity INT NULL,
  ADD COLUMN total_rejected_value DECIMAL(18,8) NULL,
  ADD COLUMN total_rotating_value DECIMAL(18,8) NULL,
  ADD COLUMN total_installment_value DECIMAL(18,8) NULL,
  ADD COLUMN total_iata_value DECIMAL(18,8) NULL,
  ADD COLUMN total_dollar_value DECIMAL(18,8) NULL,
  ADD COLUMN total_discount_value DECIMAL(18,8) NULL,
  ADD COLUMN total_liquid_value DECIMAL(18,8) NULL,
  ADD COLUMN total_tip_value DECIMAL(18,8) NULL,
  ADD COLUMN total_boarding_fee_value DECIMAL(18,8) NULL,
  ADD COLUMN accepted_cv_nsu_quantity INT NULL;

ALTER TABLE cs_archive_trailer
  ADD COLUMN total_gross_value DECIMAL(18,8) NULL,
  ADD COLUMN rejected_cv_nsu_quantity INT NULL,
  ADD COLUMN total_rejected_value DECIMAL(18,8) NULL,
  ADD COLUMN total_rotating_value DECIMAL(18,8) NULL,
  ADD COLUMN total_installment_value DECIMAL(18,8) NULL,
  ADD COLUMN total_iata_value DECIMAL(18,8) NULL,
  ADD COLUMN total_dollar_value DECIMAL(18,8) NULL,
  ADD COLUMN total_discount_value DECIMAL(18,8) NULL,
  ADD COLUMN total_liquid_value DECIMAL(18,8) NULL,
  ADD COLUMN total_tip_value DECIMAL(18,8) NULL,
  ADD COLUMN total_boarding_fee_value DECIMAL(18,8) NULL,
  ADD COLUMN accepted_cv_nsu_quantity INT NULL;

ALTER TABLE cs_rede_request_notice
  ADD COLUMN tid VARCHAR(20) NULL,
  ADD COLUMN ecommerce_order_number VARCHAR(30) NULL;

ALTER TABLE cs_serasa_consultation
  ADD COLUMN flag_id BINARY(16) NULL,
  ADD CONSTRAINT fk_cs_serasa_consultation_flag FOREIGN KEY (flag_id) REFERENCES cs_flag(id) ON UPDATE CASCADE;

CREATE INDEX idx_cs_rede_request_notice_tid ON cs_rede_request_notice(tid);
CREATE INDEX idx_cs_serasa_consultation_flag ON cs_serasa_consultation(flag_id);
