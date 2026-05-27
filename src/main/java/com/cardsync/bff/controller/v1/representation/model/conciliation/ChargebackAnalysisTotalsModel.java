package com.cardsync.bff.controller.v1.representation.model.conciliation;

import java.math.BigDecimal;

public record ChargebackAnalysisTotalsModel(
  long total,
  long requestReceived,
  long documentationDue,
  long documentationOverdue,
  long pendingDebit,
  long bankDebitScheduled,
  long netCompensationScheduled,
  long descheduled,
  long liquidatedOrLost,
  long reversedOrWon,
  BigDecimal saleValue,
  BigDecimal disputedValue,
  BigDecimal pendingValue,
  BigDecimal settledValue,
  BigDecimal compensatedValue
) {}
