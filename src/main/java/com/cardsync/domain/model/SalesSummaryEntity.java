package com.cardsync.domain.model;

import com.cardsync.domain.model.enums.StatusPaymentBankEnum;
import com.cardsync.domain.model.enums.StatusReconciliationEnum;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "cs_sales_summary")
public class SalesSummaryEntity extends AuditableEntityBase {

  private Integer agency;
  private Integer modality;
  private Integer pvNumber;
  private Integer rvNumber;
  private Integer lineNumber;
  private Integer numberCvNsu;
  private Integer currentAccount;
  private Integer creditOrderStatus;
  private Integer statusPaymentBank;
  private Integer transactionsStatus;

  private String bank;
  private String recordType;
  private String summaryType;

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

  @ManyToOne(fetch = FetchType.LAZY)
  private BankingDomicileEntity bankingDomicile;

  @ManyToOne(fetch = FetchType.LAZY)
  private ProcessedFileEntity processedFile;

  @BatchSize(size = 100)
  @OrderBy("releaseDate ASC")
  @OneToMany(mappedBy = "salesSummary", cascade = CascadeType.ALL, orphanRemoval = true)
  private Set<AdjustmentEntity> adjustments = new LinkedHashSet<>();

  @BatchSize(size = 100)
  @OrderBy("rvDate ASC")
  @OneToMany(mappedBy = "salesSummary", cascade = CascadeType.ALL, orphanRemoval = true)
  private Set<CreditOrderEntity> creditOrders = new LinkedHashSet<>();

  public StatusReconciliationEnum getCreditOrderStatus() {
    return StatusReconciliationEnum.fromCode(creditOrderStatus);
  }

  public void setCreditOrderStatus(StatusReconciliationEnum creditOrderStatus) {
    this.creditOrderStatus = Optional.ofNullable(creditOrderStatus).orElse(StatusReconciliationEnum.NULL).getCode();
  }

  public StatusPaymentBankEnum getStatusPaymentBank() {
    return StatusPaymentBankEnum.fromCode(statusPaymentBank);
  }

  public void setStatusPaymentBank(StatusPaymentBankEnum statusPaymentBank) {
    this.statusPaymentBank = (statusPaymentBank!=null ? statusPaymentBank:StatusPaymentBankEnum.NULL).getCode();
  }

  public StatusReconciliationEnum getTransactionsStatus() {
    return StatusReconciliationEnum.fromCode(transactionsStatus);
  }

  public void setTransactionsStatus(StatusReconciliationEnum transactionsStatus) {
    this.transactionsStatus = Optional.ofNullable(transactionsStatus).orElse(StatusReconciliationEnum.NULL).getCode();
  }
}
