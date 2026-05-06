package com.cardsync.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "cs_banking_domicile")
public class BankingDomicileEntity extends AuditableEntityBase {

  @Column(name = "agency")
  private Integer agency;

  @Column(name = "agency_digit", length = 5)
  private String agencyDigit;

  @Column(name = "current_account")
  private Integer currentAccount;

  @Column(name = "account_digit")
  private String accountDigit;

  @Column(name = "holder_document", length = 20)
  private String holderDocument;

  @Column(name = "holder_name", length = 120)
  private String holderName;

  @Column(name = "active", nullable = false)
  private Boolean active = Boolean.TRUE;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "bank_id")
  private BankEntity bank;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "company_id")
  private CompanyEntity company;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "establishment_id")
  private EstablishmentEntity establishment;
}
