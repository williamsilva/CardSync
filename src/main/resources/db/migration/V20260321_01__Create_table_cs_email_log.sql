CREATE TABLE cs_email_log (
  id BINARY(16) NOT NULL,

  recipient VARCHAR(320) NOT NULL,
  subject VARCHAR(300) NOT NULL,
  template VARCHAR(200) NOT NULL,

  status INT NOT NULL DEFAULT 1,
  event_type INT NOT NULL DEFAULT 1,
  error_message VARCHAR(1000),

  requested_by_id BINARY(16),

  sent_at DATETIME(6) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6),

  created_by BINARY(16),
  last_modified_by BINARY(16),

  PRIMARY KEY (id),

  CONSTRAINT fk_cs_email_log_requested_by
    FOREIGN KEY (requested_by_id) REFERENCES cs_users(id),

  INDEX idx_email_log_sent_at (sent_at),
  INDEX idx_email_log_recipient (recipient),
  INDEX idx_email_log_status (status),
  INDEX idx_email_log_requested_by (requested_by_id)

) ENGINE=InnoDB;

CREATE INDEX idx_cs_email_log_recipient ON cs_email_log (recipient);
CREATE INDEX idx_cs_email_log_sent_at ON cs_email_log (sent_at);
CREATE INDEX idx_cs_email_log_event_type ON cs_email_log (event_type);
CREATE INDEX idx_cs_email_log_requested_by ON cs_email_log (requested_by_id);