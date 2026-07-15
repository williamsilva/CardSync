CREATE TABLE cs_email_settings (
  id             UUID   NOT NULL,
  impl           VARCHAR(10)  NULL,
  from_name      VARCHAR(255) NULL,
  from_email     VARCHAR(255) NULL,
  brevo_api_key  VARCHAR(500) NULL,
  brevo_base_url VARCHAR(255) NULL,
  created_at     TIMESTAMP(6)  NOT NULL,
  updated_at     TIMESTAMP(6)  NULL,
  created_by_id  UUID   NULL,
  updated_by_id  UUID   NULL,
  PRIMARY KEY (id)
);
