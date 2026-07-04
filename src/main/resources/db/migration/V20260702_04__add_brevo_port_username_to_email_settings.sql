ALTER TABLE cs_email_settings
  ADD COLUMN brevo_port    INT          NULL,
  ADD COLUMN brevo_username VARCHAR(255) NULL;
