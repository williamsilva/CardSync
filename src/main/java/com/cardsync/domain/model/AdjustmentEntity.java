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
@Table(name = "cs_adjustment")
public class AdjustmentEntity extends AuditableEntityBase {

  private Long nsu;
  private Long letterNumber;
  private String letterReference;
  private Long numberDebitOrder;

  private Integer lineNumber;
  private Boolean ecommerce;
  private Integer pvNumber;
  private Integer installmentNumber;
  private Integer installmentTotal;
  private Integer adjustmentSequence;
  private Integer rvNumberOriginal;
  private Integer pvNumberOriginal;
  private Integer adjustmentStatus;
  private Integer adjustmentReason;
  private Integer adjustmentReason2;
  private Integer rvNumberAdjustment;
  private Integer pvNumberAdjustment;
  private Integer rvNumberInstallmentAdjusted;
  private Integer rvNumberInstallmentOriginal;

  private String tid;
  private String ecommerceOrderNumber;
  private String net;
  private String debitType;
  private String cardNumber;
  private String recordType;
  private String authorization;
  private String adjustmentType;
  private String referenceMonth;
  private String adjustmentDescription;
  private String rawAdjustmentCode;
  private String sourceRecordIdentifier;

  private LocalDate letterDate;
  private LocalDate creditDate;
  private LocalDate releaseDate;
  private LocalDate adjustmentDate;
  private LocalDate rvDateAdjusted;
  private LocalDate rvDateOriginal;
  private LocalDate transactionDate;
  private LocalDate originalDueDate;

  private BigDecimal pendingValue;
  private BigDecimal totalDebitValue;
  private BigDecimal adjustmentValue;
  private BigDecimal transactionValue;
  private BigDecimal newTransactionValue;
  private BigDecimal newInstallmentValue;
  private BigDecimal originalValueInstallment;
  private BigDecimal cancellationValueRequested;
  private BigDecimal originalGrossSalesSummaryValue;
  private BigDecimal grossValue;
  private BigDecimal liquidValue;
  private BigDecimal discountValue;

  @ManyToOne(fetch = FetchType.LAZY)
  private FlagEntity rvFlagAdjustment;

  @ManyToOne(fetch = FetchType.LAZY)
  private FlagEntity rvFlagOrigin;

  @ManyToOne(fetch = FetchType.LAZY)
  private AcquirerEntity acquirer;

  @ManyToOne(fetch = FetchType.LAZY)
  private CompanyEntity company;

  @ManyToOne(fetch = FetchType.LAZY)
  private EstablishmentEntity establishment;

  @ManyToOne(fetch = FetchType.LAZY)
  private TransactionAcqEntity transaction;

  @ManyToOne(fetch = FetchType.LAZY)
  private SalesSummaryEntity salesSummary;

  @ManyToOne(fetch = FetchType.LAZY)
  private ProcessedFileEntity processedFile;
}
