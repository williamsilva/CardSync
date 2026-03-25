package com.cardsync.domain.model;

import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;

import lombok.*;
import org.hibernate.envers.Audited;

@Getter
@Setter
@Entity
@Audited
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "cs_groups")
public class GroupEntity extends AuditableEntityBase {

  @Column(nullable = false, unique = true, length = 80)
  private String name;

  @Column(length = 100)
  private String description;

  @Builder.Default
  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(name = "cs_groups_permissions",
    joinColumns = @JoinColumn(name = "group_id"),
    inverseJoinColumns = @JoinColumn(name = "permission_id"))
  private Set<PermissionEntity> permissions = new HashSet<>();

  @Builder.Default
  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(name = "cs_users_groups",
    joinColumns = @JoinColumn(name = "group_id"),
    inverseJoinColumns = @JoinColumn(name = "user_id"))
  private Set<UserEntity> users = new HashSet<>();
}
