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

  /**
   * rvNumber DESTA parcela — nulo pra Rede (que não precisa: uma venda Rede tem 1 chave só,
   * já em {@code transaction.rvNumber}). Cielo seta este campo porque cada parcela de uma venda
   * parcelada tem sua própria "Chave UR" (achado real: 2 linhas da mesma venda em 2x, mesma
   * autorização/NSU/data, mas Chave UR diferente por parcela). Usado com prioridade sobre
   * {@code transaction.getRvNumber()} no matching de conciliação bancária — ver
   * BankReconciliationService.propagateCreditOrdersToInstallments.
   */
  private Integer rvNumber;

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
