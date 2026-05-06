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
@Table(name = "cs_installment_erp")
public class InstallmentErpEntity extends AuditableEntityBase {

  @Column(name = "gross_value")
  private BigDecimal grossValue;
  @Column(name = "liquid_value")
  private BigDecimal liquidValue;
  @Column(name = "discount_value")
  private BigDecimal discountValue;

  private Integer installment;
  @Column(name = "payment_status")
  private Integer paymentStatus;
  @Column(name = "installment_status")
  private Integer installmentStatus;
  @Column(name = "reconciliation_bank_line")
  private Integer reconciliationBankLine;
  @Column(name = "reconciliation_payment_line")
  private Integer reconciliationPaymentLine;

  @Column(name = "credit_date")
  private LocalDate creditDate;
  @Column(name = "cancellation_date")
  private LocalDate cancellationDate;
  @Column(name = "reconciliation_bank_processed_at")
  private OffsetDateTime reconciliationBankProcessedAt;
  @Column(name = "reconciliation_payment_processed_at")
  private OffsetDateTime reconciliationPaymentProcessedAt;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "reconciliation_bank_file_id")
  private ProcessedFileEntity reconciliationBankFile;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "reconciliation_payment_file_id")
  private ProcessedFileEntity reconciliationPaymentFile;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "transaction_id", nullable = false)
  private TransactionErpEntity transaction;
}
