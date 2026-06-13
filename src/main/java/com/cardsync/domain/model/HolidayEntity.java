package com.cardsync.domain.model;

import com.cardsync.domain.model.enums.StatusEnum;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Objects;

@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "cs_holiday")
public class HolidayEntity extends AuditableEntityBase {

  @Column(name = "holiday_date", nullable = false, unique = true)
  private LocalDate holidayDate;

  @Column(name = "name", length = 150, nullable = false)
  private String name;

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
