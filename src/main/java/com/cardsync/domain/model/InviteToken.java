package com.cardsync.domain.model;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.envers.Audited;

@Getter
@Setter
@Entity
@Audited
@Table(name = "cs_invite_token")
public class InviteToken extends AuditableEntityBase {

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private UserEntity user;

  @Column(nullable = false, length = 64, unique = true)
  private String tokenHash;

  @Column(nullable = false)
  private OffsetDateTime expiresAt;

  @Column
  private OffsetDateTime usedAt;

}
