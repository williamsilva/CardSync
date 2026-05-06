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
  private LocalDate creditDate;
  private OffsetDateTime saleDate;
  private OffsetDateTime saleReconciliationDate;

  private String tid;
  private String machine;
  private String statusCv;
  private String recordType;
  private String cardNumber;
  private String authorization;
  private String referenceNumber;
  private String transactionType;
  private String dccCurrency;
  private String serviceCode;

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
  private BigDecimal purchaseValue;
  private BigDecimal withdrawalValue;

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
}
