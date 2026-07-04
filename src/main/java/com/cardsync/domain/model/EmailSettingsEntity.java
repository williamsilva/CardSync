package com.cardsync.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "cs_email_settings")
public class EmailSettingsEntity extends AuditableEntityBase {

  @Column(name = "impl", length = 10)
  private String impl;

  @Column(name = "from_name", length = 255)
  private String fromName;

  @Column(name = "from_email", length = 255)
  private String fromEmail;

  @Column(name = "brevo_api_key", length = 500)
  private String brevoApiKey;

  @Column(name = "brevo_base_url", length = 255)
  private String brevoBaseUrl;

  @Column(name = "brevo_port")
  private Integer brevoPort;

  @Column(name = "brevo_username", length = 255)
  private String brevoUsername;

  @Column(name = "chargeback_recipients", columnDefinition = "TEXT")
  private String chargebackRecipients;

  @Column(name = "smtp_host", length = 255)
  private String smtpHost;

  @Column(name = "smtp_port")
  private Integer smtpPort;

  @Column(name = "smtp_username", length = 255)
  private String smtpUsername;

  @Column(name = "smtp_password", length = 500)
  private String smtpPassword;

  @Column(name = "smtp_auth")
  private Boolean smtpAuth;

  @Column(name = "smtp_starttls")
  private Boolean smtpStarttls;

  @Column(name = "smtp_ssl")
  private Boolean smtpSsl;
}
