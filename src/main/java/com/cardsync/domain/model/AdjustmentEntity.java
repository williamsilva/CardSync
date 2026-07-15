package com.cardsync.domain.model;

import com.cardsync.domain.model.enums.AdjustmentReasonEnum;
import com.cardsync.domain.model.enums.AdjustmentStatusEnum;
import jakarta.persistence.Column;
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
  private Long numberDebitOrder;

  private Integer pvNumber;
  private Boolean ecommerce;
  private Integer lineNumber;
  private Integer rvNumberOriginal;
  private Integer pvNumberOriginal;
  private Integer adjustmentStatus;
  private Integer adjustmentReason;
  private Integer installmentTotal;
  private Integer installmentNumber;
  private Integer adjustmentReason2;
  private Integer adjustmentSequence;
  private Integer rvNumberAdjustment;
  private Integer pvNumberAdjustment;
  private Integer rvNumberInstallmentAdjusted;
  private Integer rvNumberInstallmentOriginal;

  private String tid;
  private String net;
  private String debitType;
  private String cardNumber;
  private String recordType;

  // "authorization" é palavra reservada no Postgres (AUTHORIZATION) - precisa de identificador
  // entre aspas; o Hibernate traduz backtick para o quote-char do dialect alvo.
  @Column(name = "`authorization`")
  private String authorization;
  private String adjustmentType;
  private String referenceMonth;
  private String letterReference;
  private String rawAdjustmentCode;
  private String ecommerceOrderNumber;
  private String adjustmentDescription;
  private String sourceRecordIdentifier;

  private LocalDate letterDate;
  private LocalDate creditDate;
  private LocalDate releaseDate;
  private LocalDate adjustmentDate;
  private LocalDate rvDateAdjusted;
  private LocalDate rvDateOriginal;
  private LocalDate transactionDate;
  private LocalDate originalDueDate;

  private BigDecimal grossValue;
  private BigDecimal liquidValue;
  private BigDecimal pendingValue;
  private BigDecimal discountValue;
  private BigDecimal totalDebitValue;
  private BigDecimal adjustmentValue;
  private BigDecimal transactionValue;
  private BigDecimal newTransactionValue;
  private BigDecimal newInstallmentValue;
  private BigDecimal originalValueInstallment;
  private BigDecimal cancellationValueRequested;
  private BigDecimal originalGrossSalesSummaryValue;

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

  public AdjustmentStatusEnum getAdjustmentStatus() {
    AdjustmentStatusEnum value = AdjustmentStatusEnum.fromCode(adjustmentStatus);
    return value != null ? value : AdjustmentStatusEnum.NULL;
  }

  public void setAdjustmentStatus(AdjustmentStatusEnum adjustmentStatus) {
    this.adjustmentStatus = (adjustmentStatus!=null ? adjustmentStatus:AdjustmentStatusEnum.NULL).getCode();
  }

  public AdjustmentReasonEnum getAdjustmentReason() {
    AdjustmentReasonEnum value = AdjustmentReasonEnum.fromCode(adjustmentReason);
    return value != null ? value : AdjustmentReasonEnum.NULL;
  }

  public void setAdjustmentReason(AdjustmentReasonEnum adjustmentReason) {
    this.adjustmentReason = (adjustmentReason!=null ? adjustmentReason:AdjustmentReasonEnum.NULL).getCode();
  }
}