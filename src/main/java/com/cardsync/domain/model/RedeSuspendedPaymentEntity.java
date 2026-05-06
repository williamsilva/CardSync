package com.cardsync.domain.model;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
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
@Table(name = "cs_rede_suspended_payment")
public class RedeSuspendedPaymentEntity extends AuditableEntityBase {

  private String recordType;
  private Integer lineNumber;
  private Integer pvNumber;
  private Long creditOrderNumber;
  private BigDecimal creditOrderValue;
  private LocalDate releaseDate;
  private LocalDate originalDueDate;
  private Integer rvNumber;
  private LocalDate rvDate;
  private LocalDate suspensionDate;
  private String paymentType;
  private String flagCode;
  private Long redeContractNumber;
  private LocalDate contractUpdateDate;
  private Integer installmentNumber;
  private LocalDate originalContractDate;
  private String cipContractNumber;

  @ManyToOne(fetch = FetchType.LAZY)
  private FlagEntity flag;

  @ManyToOne(fetch = FetchType.LAZY)
  private AcquirerEntity acquirer;

  @ManyToOne(fetch = FetchType.LAZY)
  private CompanyEntity company;

  @ManyToOne(fetch = FetchType.LAZY)
  private EstablishmentEntity establishment;

  @ManyToOne(fetch = FetchType.LAZY)
  private SalesSummaryEntity salesSummary;

  @ManyToOne(fetch = FetchType.LAZY)
  private ProcessedFileEntity processedFile;
}
