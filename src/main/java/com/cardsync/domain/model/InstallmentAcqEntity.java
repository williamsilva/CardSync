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
@Table(name = "cs_installment_acq")
public class InstallmentAcqEntity extends AuditableEntityBase {

  private BigDecimal grossValue;
  private BigDecimal liquidValue;
  private BigDecimal discountValue;
  private BigDecimal adjustmentValue;

  private Integer installment;
  private Integer statusPaymentBank;
  private Integer installmentStatus;
  private Integer reconciliationBankLine;

  private LocalDate paymentDate;
  private LocalDate cancellationDate;
  private LocalDate expectedPaymentDate;

  private OffsetDateTime reconciliationBankProcessedAt;

  @ManyToOne(fetch = FetchType.LAZY)
  private ProcessedFileEntity reconciliationBankFile;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "transaction_id", nullable = false)
  private TransactionAcqEntity transaction;

  @ManyToOne(fetch = FetchType.LAZY)
  private CreditOrderEntity creditOrder;

  @ManyToOne(fetch = FetchType.LAZY)
  private ReleasesBankEntity releaseBank;

  @ManyToOne(fetch = FetchType.LAZY)
  private AdjustmentEntity adjustment;
}
