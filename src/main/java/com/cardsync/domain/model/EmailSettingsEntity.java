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
}
