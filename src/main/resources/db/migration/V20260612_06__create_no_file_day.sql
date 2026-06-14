CREATE TABLE cs_no_file_day (
  id BINARY(16) NOT NULL,
  no_file_date DATE NOT NULL,
  description VARCHAR(255) NOT NULL,
  day_type INT NOT NULL,
  file_group VARCHAR(10) NOT NULL,
  bank_id BINARY(16) NULL,
  acquirer_id BINARY(16) NULL,
  status INT NOT NULL DEFAULT 1,
  status_date DATETIME(6) NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NULL,
  created_by_id BINARY(16) NULL,
  updated_by_id BINARY(16) NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_cs_no_file_day_bank FOREIGN KEY (bank_id) REFERENCES cs_bank (id),
  CONSTRAINT fk_cs_no_file_day_acquirer FOREIGN KEY (acquirer_id) REFERENCES cs_acquirer (id),
  CONSTRAINT fk_cs_no_file_day_created_by FOREIGN KEY (created_by_id) REFERENCES cs_users (id),
  CONSTRAINT fk_cs_no_file_day_updated_by FOREIGN KEY (updated_by_id) REFERENCES cs_users (id)
);

CREATE INDEX idx_cs_no_file_day_status_date ON cs_no_file_day (status, no_file_date);
CREATE INDEX idx_cs_no_file_day_group ON cs_no_file_day (file_group, no_file_date);