package com.cardsync.bff.controller.v1.representation.model.erp;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TransactionErpInstallmentModel {

  private UUID id;

  private Integer installment;
  private Integer paymentStatus;
  private Integer installmentStatus;
  private Integer reconciliationBankLine;
  private Integer reconciliationPaymentLine;

  private BigDecimal grossValue;
  private BigDecimal netValue;
  private BigDecimal feeValue;

  private LocalDate cancellationDate;
  private LocalDate expectedPaymentDate;

  private OffsetDateTime reconciliationBankProcessedAt;
  private OffsetDateTime reconciliationPaymentProcessedAt;
}
