package com.cardsync.domain.model;

import jakarta.persistence.*;
import java.util.UUID;

import lombok.*;
import org.hibernate.envers.Audited;

@Getter
@Setter
@Entity
@Audited
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "cs_permissions")
public class PermissionEntity extends AuditableEntityBase {

  @Column(nullable = false, unique = true, length = 120)
  private String name;

  @Column(nullable = false, length = 120)
  private String description;
}
