package com.cardsync.domain.model;

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
  private Integer transactionStatus;
  private Integer statusPaymentBank;
  private Integer transactionStatusReason;

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

  @BatchSize(size = 100)
  @OrderBy("installment ASC")
  @OneToMany(mappedBy = "transaction", cascade = CascadeType.ALL, orphanRemoval = true)
  private Set<InstallmentAcqEntity> installments = new LinkedHashSet<>();
}
