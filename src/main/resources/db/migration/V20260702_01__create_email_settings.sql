CREATE TABLE cs_email_settings (
  id             BINARY(16)   NOT NULL,
  impl           VARCHAR(10)  NULL,
  from_name      VARCHAR(255) NULL,
  from_email     VARCHAR(255) NULL,
  brevo_api_key  VARCHAR(500) NULL,
  brevo_base_url VARCHAR(255) NULL,
  created_at     DATETIME(6)  NOT NULL,
  updated_at     DATETIME(6)  NULL,
  created_by_id  BINARY(16)   NULL,
  updated_by_id  BINARY(16)   NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_cs_email_settings_created_by FOREIGN KEY (created_by_id) REFERENCES cs_users (id),
  CONSTRAINT fk_cs_email_settings_updated_by FOREIGN KEY (updated_by_id) REFERENCES cs_users (id)
);
