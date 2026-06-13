package com.cardsync.domain.model;

import com.cardsync.domain.model.enums.StatusEnum;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.Objects;

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

  @Column(name = "status", nullable = false)
  private Integer status = StatusEnum.ACTIVE.getCode();

  @Column(name = "status_date")
  private OffsetDateTime statusDate;

  public StatusEnum getStatus() {
    return StatusEnum.fromCode(status);
  }

  public void setStatus(StatusEnum status) {
    StatusEnum normalizedStatus = status != null ? status : StatusEnum.NULL;
    Integer newCode = normalizedStatus.getCode();

    if (!Objects.equals(this.status, newCode)) {
      this.statusDate = OffsetDateTime.now();
    }

    this.status = newCode;
  }

  public void activate() {
    setStatus(StatusEnum.ACTIVE);
  }

  public void inactivate() {
    setStatus(StatusEnum.INACTIVE);
  }

  public void block() {
    setStatus(StatusEnum.BLOCKED);
  }
}
