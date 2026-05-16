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

  private BigDecimal grossValue;
  private BigDecimal liquidValue;
  private BigDecimal discountValue;

  private Integer installment;
  private Integer statusPaymentBank;
  private Integer installmentStatus;
  private Integer reconciliationBankLine;
  private Integer reconciliationPaymentLine;

  private LocalDate paymentDate;
  private LocalDate cancellationDate;
  private LocalDate expectedPaymentDate;

  private OffsetDateTime reconciliationBankProcessedAt;
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
