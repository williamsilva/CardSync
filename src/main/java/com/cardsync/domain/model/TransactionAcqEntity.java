package com.cardsync.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "cs_transaction_acq")
public class TransactionAcqEntity extends AuditableEntityBase {

  private Long nsu;

  private LocalDate canceledDate;
  private OffsetDateTime saleDate;
  private OffsetDateTime saleReconciliationDate;

  private String tid;
  private String machine;
  private String statusCv;
  private String recordType;
  private String cardNumber;
  private String authorization;
  private String referenceNumber;

  private Integer capture;
  private Integer modality;
  private Integer rvNumber;
  private Integer lineNumber;
  private Integer statusAudit;
  private Integer installment;
  private Integer transactionStatus;
  private Integer statusPaymentBank;
  private Integer transactionStatusReason;

  private BigDecimal mdrRate;
  private BigDecimal flexRate;
  private BigDecimal tipValue;
  private BigDecimal grossValue;
  private BigDecimal liquidValue;
  private BigDecimal discountValue;
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

  /*
  @ManyToOne(fetch = FetchType.LAZY)
  private ProcessedFile processedFile;

  @ManyToOne(fetch = FetchType.LAZY)
  private SalesSummary salesSummary;

  @ManyToOne(fetch = FetchType.LAZY)
  private EstablishmentEntity establishment;

  @OrderBy("installment ASC")
  @OneToMany(mappedBy = "transaction", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<InstallmentAcq> installments = new ArrayList<>();

  @OneToMany(mappedBy = "transaction")
  private List<Adjustment> adjustments = new ArrayList<>();

  public StatusTransactionAuditEnum getStatusAudit() {
    return StatusTransactionAuditEnum.toEnum(statusAudit);
  }

  public void setStatusAudit(StatusTransactionAuditEnum statusAudit) {
    this.statusAudit = Optional.ofNullable(statusAudit).orElse(StatusTransactionAuditEnum.NULL).getCode();
  }

  public CaptureEnum getCapture() {
    return CaptureEnum.toEnum(capture);
  }

  public void setCapture(CaptureEnum capture) {
    this.capture = Optional.ofNullable(capture).orElse(CaptureEnum.NULL).getCode();
  }

  public ModalityEnum getModality() {
    return ModalityEnum.toEnum(modality);
  }

  public void setModality(ModalityEnum modality) {
    this.modality = Optional.ofNullable(modality).orElse(ModalityEnum.NULL).getCode();
  }

  public StatusTransactionEnum getTransactionStatus() {
    return StatusTransactionEnum.toEnum(transactionStatus);
  }

  public void setTransactionStatus(StatusTransactionEnum transactionStatus) {
    this.transactionStatus = Optional.ofNullable(transactionStatus).orElse(StatusTransactionEnum.NULL).getCode();
  }

  public StatusTransactionReasonEnum getTransactionStatusReason() {
    return StatusTransactionReasonEnum.toEnum(transactionStatusReason);
  }

  public void setTransactionStatusReason(StatusTransactionReasonEnum transactionStatusReason) {
    this.transactionStatusReason = Optional.ofNullable(transactionStatusReason).orElse(StatusTransactionReasonEnum.NULL).getCode();
  }

  public StatusPaymentBankEnum getStatusPaymentBank() {
    return StatusPaymentBankEnum.toEnum(statusPaymentBank);
  }

  public void setStatusPaymentBank(StatusPaymentBankEnum statusPaymentBank) {
    this.statusPaymentBank = Optional.ofNullable(statusPaymentBank).orElse(StatusPaymentBankEnum.NULL).getCode();
  }

  public void cancel(Adjustment adj) {
    setAdjustment(adj);
    setCanceledDate(adj.getAdjustmentDate());
    setTransactionStatus(StatusTransactionEnum.CANCELED);
  }*/
}
