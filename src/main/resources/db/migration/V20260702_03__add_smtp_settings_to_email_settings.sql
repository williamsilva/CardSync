ALTER TABLE cs_email_settings
  ADD COLUMN smtp_host       VARCHAR(255),
  ADD COLUMN smtp_port       INT,
  ADD COLUMN smtp_username   VARCHAR(255),
  ADD COLUMN smtp_password   VARCHAR(500),
  ADD COLUMN smtp_auth       BOOLEAN,
  ADD COLUMN smtp_starttls   BOOLEAN,
  ADD COLUMN smtp_ssl        BOOLEAN;
