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
@Table(name = "cs_bank")
public class BankEntity extends AuditableEntityBase {

  @Column(name = "code", length = 10, nullable = false)
  private String code;

  @Column(name = "name", length = 100, nullable = false)
  private String name;

  @Column(name = "ispb", length = 20)
  private String ispb;

  @Column(name = "active", nullable = false)
  private Boolean active = Boolean.TRUE;
}
