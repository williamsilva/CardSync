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
@Table(name = "ws_sales_summary")
public class SalesSummaryEntity extends AuditableEntityBase {

  private Integer modality;
  private Integer pvNumber;
  private Integer rvNumber;
  private Integer lineNumber;
  private Integer numberCvNsu;
  private Integer creditOrderStatus;
  private Integer statusPaymentBank;
  private Integer transactionsStatus;

  private String recordType;

  private BigDecimal tipValue;
  private BigDecimal grossValue;
  private BigDecimal liquidValue;
  private BigDecimal adjustedValue;
  private BigDecimal discountValue;
  private BigDecimal rejectedValue;

  private Boolean manualGenerated;

  private LocalDate rvDate;
  private LocalDate firstInstallmentCreditDate;

  @ManyToOne(fetch = FetchType.LAZY)
  private FlagEntity flag;

  @ManyToOne(fetch = FetchType.LAZY)
  private AcquirerEntity acquirer;

  @ManyToOne(fetch = FetchType.LAZY)
  private CompanyEntity company;
/*
  @ManyToOne(fetch = FetchType.LAZY)
  private ProcessedFile processedFile;

  @ManyToOne(fetch = FetchType.LAZY)
  private BankingDomicile bankingDomicile;

  @OneToMany(mappedBy = "salesSummary")
  private List<Adjustment> adjustments = new ArrayList<>();

  @OneToMany(mappedBy = "salesSummary")
  private List<CreditOrder> creditOrders = new ArrayList<>();

  @OneToMany(mappedBy = "salesSummary", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<TransactionAcq> transactions = new ArrayList<>();

  public ModalityEnum getModality() {
    return ModalityEnum.toEnum(modality);
  }

  public void setModality(ModalityEnum modality) {
    this.modality = Optional.ofNullable(modality).orElse(ModalityEnum.NULL).getCode();
  }

  public ReconciliationStatusEnum getCreditOrderStatus() {
    return ReconciliationStatusEnum.toEnum(creditOrderStatus);
  }

  public void setCreditOrderStatus(ReconciliationStatusEnum creditOrderStatus) {
    this.creditOrderStatus = Optional.ofNullable(creditOrderStatus).orElse(ReconciliationStatusEnum.NULL).getCode();
  }

  public ReconciliationStatusEnum getTransactionsStatus() {
    return ReconciliationStatusEnum.toEnum(transactionsStatus);
  }

  public void setTransactionsStatus(ReconciliationStatusEnum transactionsStatus) {
    this.transactionsStatus = Optional.ofNullable(transactionsStatus).orElse(ReconciliationStatusEnum.NULL).getCode();
  }

  public StatusPaymentBankEnum getStatusPaymentBank() {
    return StatusPaymentBankEnum.toEnum(statusPaymentBank);
  }

  public void setStatusPaymentBank(StatusPaymentBankEnum statusPaymentBank) {
    this.statusPaymentBank = (statusPaymentBank!=null ? statusPaymentBank:StatusPaymentBankEnum.NULL).getCode();
  }*/
}
