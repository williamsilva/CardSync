package com.cardsync.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "cs_releases_bank")
public class ReleasesBankEntity extends AuditableEntityBase {

  private Integer lineNumber;
  private Integer serviceLot;
  private Integer numberParcels;
  private Integer releaseCategory;
  private Integer sequentialNumber;
  private Integer numberCreditOrders;
  private Integer historicalCodeBank;
  private Integer modalityPaymentBank;
  private Integer releaseCategoryCode;
  private Integer reconciliationStatus;
  private Integer typeComplementRelease;
  private Integer companyRegistrationType;
  private Integer numberReconciliations;

  private String recordType;
  private String segmentCode;
  private String releaseType;
  private String natureRelease;
  private String bankAgreementCode;
  private String complementRelease;
  private String documentComplementNumber;
  private String descriptionHistoricalBank;
  private String cpmfExemptionIdentification;

  private BigDecimal releaseValue;

  private LocalDate releaseDate;
  private LocalDate accountingDate;

  @ManyToOne(fetch = FetchType.LAZY)
  private FlagEntity flag;

  @ManyToOne(fetch = FetchType.LAZY)
  private BankEntity bank;

  @ManyToOne(fetch = FetchType.LAZY)
  private AcquirerEntity acquirer;

  @ManyToOne(fetch = FetchType.LAZY)
  private CompanyEntity company;

  @ManyToOne(fetch = FetchType.LAZY)
  private EstablishmentEntity establishment;

  @ManyToOne(fetch = FetchType.LAZY)
  private ProcessedFileEntity processedFile;

  @ManyToOne(fetch = FetchType.LAZY)
  private BankingDomicileEntity bankingDomicile;
}
