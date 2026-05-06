package com.cardsync.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "cs_origin_file")
public class OriginFileEntity extends AuditableEntityBase {
  @Column(nullable = false, length = 30, unique = true)
  private String code;
  @Column(nullable = false, length = 80)
  private String name;
  @Column(length = 150)
  private String description;
}
