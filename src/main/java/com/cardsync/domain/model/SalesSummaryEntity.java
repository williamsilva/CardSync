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
@Table(name = "cs_sales_summary")
public class SalesSummaryEntity extends AuditableEntityBase {

  private Integer modality;
  private Integer pvNumber;
  private Integer rvNumber;
  private Integer lineNumber;
  private Integer numberCvNsu;
  private Integer creditOrderStatus;
  private Integer statusPaymentBank;
  private Integer transactionsStatus;
  private Integer bank;
  private Integer agency;
  private Integer currentAccount;

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
}
