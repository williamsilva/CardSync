package com.cardsync.domain.model;

import com.cardsync.domain.model.enums.StatusUserEnum;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.envers.Audited;

@Getter
@Setter
@Entity
@Audited
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "cs_users")
public class UserEntity extends AuditableEntityBase {

  @Column(nullable = false, length = 120)
  private String name;

  @Column(nullable = false)
  private Integer status = StatusUserEnum.ACTIVE.getCode();

  @Column(nullable = false)
  private int failedAttempts = 0;

  @Column(nullable = false, length = 120, unique = true)
  private String userName;

  @Column(nullable = false, length = 15, unique = true)
  private String document;

  @Column
  private OffsetDateTime blockedUntil;

  @Column
  private OffsetDateTime lastLoginAt;

  @Column(nullable = false, length = 255)
  private String passwordHash;

  @Column
  private OffsetDateTime passwordChangedAt;

  @Column
  private OffsetDateTime passwordExpiresAt;

  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
    name = "cs_users_groups",
    joinColumns = @JoinColumn(name = "user_id"),
    inverseJoinColumns = @JoinColumn(name = "group_id")
  )
  private Set<GroupEntity> groups = new HashSet<>();

  public StatusUserEnum getStatusEnum() {
    return StatusUserEnum.fromCode(this.status);
  }

  public void setStatusEnum(StatusUserEnum statusEnum) {
    this.status = StatusUserEnum.toCode(statusEnum);
  }

  public boolean isBlocked(OffsetDateTime nowUtc) {
    return blockedUntil != null && blockedUntil.isAfter(nowUtc);
  }

  public boolean isExpiredPassword(OffsetDateTime nowUtc) {
    return passwordExpiresAt != null && !passwordExpiresAt.isAfter(nowUtc);
  }

  public boolean isEnabled() {
    StatusUserEnum statusEnum = getStatusEnum();
    return statusEnum != null && statusEnum.canLogin();
  }

  public boolean isPendingPasswordUser() {
    StatusUserEnum statusEnum = getStatusEnum();
    return statusEnum != null && statusEnum.isPendingPassword();
  }

  public boolean isInactiveUser() {
    StatusUserEnum statusEnum = getStatusEnum();
    return statusEnum != null && statusEnum.isInactive();
  }

  public boolean canAuthenticate(OffsetDateTime nowUtc) {
    StatusUserEnum statusEnum = getStatusEnum();

    return statusEnum != null
      && statusEnum.canLogin()
      && !isBlocked(nowUtc)
      && !isExpiredPassword(nowUtc);
  }
}