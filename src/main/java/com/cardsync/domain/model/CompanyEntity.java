package com.cardsync.domain.model;

import com.cardsync.domain.model.enums.StatusEnum;
import com.cardsync.domain.model.enums.TypeCompanyEnum;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "cs_company")
public class CompanyEntity extends AuditableEntityBase {

  private String cnpj;
  private Integer type;
  private Integer status;
  private String fantasyName;
  private String socialReason;

  @OneToMany(mappedBy = "company")
  private List<EstablishmentEntity> establishments = new ArrayList<>();

  @OneToMany(mappedBy = "company")
  private List<TransactionAcqEntity> transactions = new ArrayList<>();

  @OneToMany(mappedBy = "company")
  private List<CompanyAcquirerEntity> companyAcquirers = new ArrayList<>();

  public StatusEnum getStatus() {
    return StatusEnum.fromCode(status);
  }

  public void setStatus(StatusEnum status) {
    this.status = (status!=null ? status:StatusEnum.NULL).getCode();
  }

  public TypeCompanyEnum getType() {
    return TypeCompanyEnum.fromCode(type);
  }

  public void setType(TypeCompanyEnum type) {
    this.type = (type!=null ? type:TypeCompanyEnum.NULL).getCode();
  }

  public void inactivate() {
    setStatus(StatusEnum.INACTIVE);
  }

  public void activate() {
    setStatus(StatusEnum.ACTIVE);
  }

  public void block() {
    setStatus(StatusEnum.BLOCKED);
  }
}
