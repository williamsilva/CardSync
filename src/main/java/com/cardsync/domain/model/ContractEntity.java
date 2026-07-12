package com.cardsync.domain.model;

import com.cardsync.domain.model.enums.ContractEnum;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "cs_contracts")
public class ContractEntity extends AuditableEntityBase {

  @Column(nullable = false)
  private Integer status;

  @Column(nullable = false, length = 150)
  private String description;

  private LocalDate endDate;

  @Column(name = "start_date", nullable = false)
  private LocalDate startDate;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "company_id")
  private CompanyEntity company;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "acquirer_id", nullable = false)
  private AcquirerEntity acquirer;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "establishment_id")
  private EstablishmentEntity establishment;

  @OneToMany(mappedBy = "contract", cascade = CascadeType.ALL, orphanRemoval = true)
  private Set<ContractFlagEntity> contractFlags = new LinkedHashSet<>();

  public ContractEnum getStatus() {
    return ContractEnum.fromCode(status);
  }

  public void setStatus(ContractEnum status) {
    this.status = (status != null ? status : ContractEnum.NULL).getCode();
  }

  public void validity() {
    setStatus(ContractEnum.VALIDITY);
  }

  public void expired() {
    setStatus(ContractEnum.EXPIRED);
  }

  public void closed() {
    setStatus(ContractEnum.CLOSED);
  }
}
