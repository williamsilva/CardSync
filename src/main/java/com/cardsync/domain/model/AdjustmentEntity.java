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
  private Long numberDebitOrder;

  private Integer lineNumber;
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
  private String net;
  private String debitType;
  private String cardNumber;
  private String recordType;
  private String authorization;
  private String adjustmentType;
  private String referenceMonth;
  private String adjustmentDescription;

  private LocalDate letterDate;
  private LocalDate creditDate;
  private LocalDate adjustmentDate;
  private LocalDate rvDateAdjusted;
  private LocalDate rvDateOriginal;
  private LocalDate transactionDate;

  private BigDecimal pendingValue;
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
  private TransactionAcqEntity transaction;

  @ManyToOne(fetch = FetchType.LAZY)
  private SalesSummaryEntity salesSummary;

  /*
  @ManyToOne(fetch = FetchType.LAZY)
  private ProcessedFile processedFile;

  public AdjustmentTypeEnum getAdjustmentType() {
    return AdjustmentTypeEnum.toEnum(adjustmentType);
  }

  public void setAdjustmentType(AdjustmentTypeEnum adjustmentType) {
    this.adjustmentType = (adjustmentType!=null ? adjustmentType:AdjustmentTypeEnum.NULL).getCode();
  }

  public AdjustmentReasonEnum getAdjustmentReason() {
    return AdjustmentReasonEnum.toEnum(adjustmentReason);
  }

  public void setAdjustmentReason(AdjustmentReasonEnum adjustmentReason) {
    this.adjustmentReason = (adjustmentReason!=null ? adjustmentReason:AdjustmentReasonEnum.NULL).getCode();
  }

  public AdjustmentStatusEnum getAdjustmentStatus() {
    return AdjustmentStatusEnum.toEnum(adjustmentStatus);
  }

  public void setAdjustmentStatus(AdjustmentStatusEnum adjustmentStatus) {
    this.adjustmentStatus = Optional.ofNullable(adjustmentStatus).orElse(AdjustmentStatusEnum.NULL).getCode();
  }*/
}
