CREATE TABLE cs_processed_file_error (
  id BINARY(16) NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  created_by_id BINARY(16) NULL,
  updated_by_id BINARY(16) NULL,
  processed_file_id BINARY(16) NOT NULL,
  line_number INT NULL,
  error_type VARCHAR(30) NOT NULL,
  error_code VARCHAR(80) NOT NULL,
  message VARCHAR(500) NOT NULL,
  raw_line TEXT NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_cs_processed_file_error_file FOREIGN KEY (processed_file_id) REFERENCES cs_processed_file(id) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB;

CREATE INDEX idx_cs_processed_file_error_file ON cs_processed_file_error(processed_file_id);
CREATE INDEX idx_cs_processed_file_error_type_code ON cs_processed_file_error(error_type, error_code);
CREATE INDEX idx_cs_processed_file_error_line ON cs_processed_file_error(line_number);
