package com.cardsync.domain.model;

import jakarta.persistence.*;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.envers.Audited;

@Getter
@Setter
@Entity
@Audited
@Table(name = "cs_password_history")
public class PasswordHistory extends AuditableEntityBase {

  @Column(columnDefinition = "BINARY(16)", nullable = false)
  private UUID userId;

  @Column(nullable = false, length = 255)
  private String passwordHash;

}
