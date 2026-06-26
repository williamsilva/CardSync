package com.cardsync.domain.model;

import com.cardsync.domain.model.enums.FeeReconciliationStatusEnum;
import com.cardsync.domain.model.enums.StatusPaymentBankEnum;
import com.cardsync.domain.model.enums.StatusTransactionEnum;
import com.cardsync.domain.model.enums.StatusTransactionReasonEnum;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "cs_transaction_acq")
public class TransactionAcqEntity extends AuditableEntityBase {

  private Long nsu;

  private LocalDate creditDate;
  private LocalDate canceledDate;
  private OffsetDateTime saleDate;
  private OffsetDateTime saleReconciliationDate;

  private String tid;
  private String machine;
  private String statusCv;
  private String recordType;
  private String cardNumber;
  private String dccCurrency;
  private String serviceCode;
  private String authorization;
  private String referenceNumber;
  private String transactionType;

  private Integer capture;
  private Integer modality;
  private Integer rvNumber;
  private Integer lineNumber;
  private Integer statusAudit;
  private Integer installment;
  private Integer statusTransaction;
  private Integer statusPaymentBank;
  private Integer statusTransactionReason;
  private Integer feeReconciliationStatus = FeeReconciliationStatusEnum.PENDING.getCode();

  private BigDecimal mdrRate;
  private BigDecimal flexRate;
  private BigDecimal tipValue;
  private BigDecimal grossValue;
  private BigDecimal liquidValue;
  private BigDecimal discountValue;
  private BigDecimal purchaseValue;
  private BigDecimal withdrawalValue;
  private BigDecimal firstInstallmentValue;
  private BigDecimal otherInstallmentsValue;

  @ManyToOne(fetch = FetchType.LAZY)
  private FlagEntity flag;

  @ManyToOne(fetch = FetchType.LAZY)
  private AdjustmentEntity adjustment;

  @ManyToOne(fetch = FetchType.LAZY)
  private AcquirerEntity acquirer;

  @ManyToOne(fetch = FetchType.LAZY)
  private CompanyEntity company;

  @ManyToOne(fetch = FetchType.LAZY)
  private ProcessedFileEntity processedFile;

  @ManyToOne(fetch = FetchType.LAZY)
  private SalesSummaryEntity salesSummary;

  @ManyToOne(fetch = FetchType.LAZY)
  private EstablishmentEntity establishment;

  @BatchSize(size = 1000)
  @OrderBy("installment ASC")
  @OneToMany(mappedBy = "transaction", cascade = CascadeType.ALL, orphanRemoval = true)
  private Set<InstallmentAcqEntity> installments = new LinkedHashSet<>();

  public StatusPaymentBankEnum getStatusPaymentBank() {
    return StatusPaymentBankEnum.fromCode(statusPaymentBank);
  }

  public void setStatusPaymentBank(StatusPaymentBankEnum statusPaymentBank) {
    this.statusPaymentBank = (statusPaymentBank!=null ? statusPaymentBank:StatusPaymentBankEnum.NULL).getCode();
  }

  public StatusTransactionEnum getStatusTransaction() {
    return StatusTransactionEnum.fromCode(statusTransaction);
  }

  public void setStatusTransaction(StatusTransactionEnum statusTransaction) {
    this.statusTransaction = Optional.ofNullable(statusTransaction).orElse(StatusTransactionEnum.NULL).getCode();
  }

  public StatusTransactionReasonEnum getStatusTransactionReason() {
    return StatusTransactionReasonEnum.fromCode(statusTransactionReason);
  }

  public void setStatusTransactionReason(StatusTransactionReasonEnum statusTransactionReason) {
    this.statusTransactionReason = StatusTransactionReasonEnum.toCode(statusTransactionReason);
  }

  public FeeReconciliationStatusEnum getFeeReconciliationStatus() {
    return FeeReconciliationStatusEnum.fromCode(feeReconciliationStatus);
  }

  public void setFeeReconciliationStatus(FeeReconciliationStatusEnum feeReconciliationStatus) {
    this.feeReconciliationStatus = FeeReconciliationStatusEnum.toCode(feeReconciliationStatus);
  }
}
