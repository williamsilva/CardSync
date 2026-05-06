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
@Table(name = "cs_credit_order")
public class CreditOrderEntity extends AuditableEntityBase {

  private Long creditOrderNumber;

  private String launchType;
  private String recordType;

  private Integer rvNumber;
  private Integer lineNumber;
  private Integer creditStatus;
  private Integer pvCentralizer;
  private Integer transactionType;
  private Integer originalPvNumber;
  private Integer installmentTotal;
  private Integer statusPaymentBank;
  private Integer installmentNumber;
  private Integer salesSummaryStatus;
  private Integer reconciliationStatus;

  private LocalDate rvDate;
  private LocalDate releaseDate;
  private LocalDate creditOrderDate;

  private BigDecimal releaseValue;
  private BigDecimal grossRvValue;
  private BigDecimal discountRateValue;

  @ManyToOne(fetch = FetchType.LAZY)
  private FlagEntity flag;

  @ManyToOne(fetch = FetchType.LAZY)
  private AcquirerEntity acquirer;

  @ManyToOne(fetch = FetchType.LAZY)
  private CompanyEntity company;

  @ManyToOne(fetch = FetchType.LAZY)
  private ProcessedFileEntity processedFile;

  @ManyToOne(fetch = FetchType.LAZY)
  private SalesSummaryEntity salesSummary;

  @ManyToOne(fetch = FetchType.LAZY)
  private BankingDomicileEntity bankingDomicile;

  @ManyToOne(fetch = FetchType.LAZY)
  private ReleasesBankEntity releaseBank;
}
