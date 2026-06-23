package com.cardsync.domain.model;

import com.cardsync.domain.model.enums.StatusEnum;
import com.cardsync.domain.model.enums.TypeEstablishmentEnum;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "cs_establishment")
public class EstablishmentEntity extends AuditableEntityBase {

  private Integer type;
  private Integer status;
  private Integer pvNumber;

  @Column(name = "opening_date")
  private LocalDate openingDate;

  @Column(name = "closing_date")
  private LocalDate closingDate;

  @ManyToOne(fetch = FetchType.LAZY)
  private AcquirerEntity acquirer;

  @ManyToOne(fetch = FetchType.LAZY)
  private CompanyEntity company;

  public TypeEstablishmentEnum getType() {
    return TypeEstablishmentEnum.fromCode(type);
  }

  public void setType(TypeEstablishmentEnum type) {
    this.type = (type!=null ? type:TypeEstablishmentEnum.NULL).getCode();
  }

  public StatusEnum getStatus() {
    return StatusEnum.fromCode(status);
  }

  public void setStatus(StatusEnum status) {
    this.status = (status!=null ? status:StatusEnum.NULL).getCode();
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