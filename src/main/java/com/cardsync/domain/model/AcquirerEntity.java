package com.cardsync.domain.model;

import com.cardsync.domain.model.enums.StatusEnum;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "cs_acquirer")
public class AcquirerEntity extends AuditableEntityBase {

  private Integer status;

  private String cnpj;
  private String fantasyName;
  private String socialReason;
  private String fileIdentifier;

  @OneToMany(mappedBy = "acquirer", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<RelationAcquirerEstablishmentEntity> acquirerEstablishments = new ArrayList<>();

  @OneToMany(mappedBy = "acquirer", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<RelationAcquirerCompanyEntity> acquirerCompanies = new ArrayList<>();

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
