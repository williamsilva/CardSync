package com.cardsync.bff.controller.v1.representation.model.conciliation;

import java.math.BigDecimal;

public record ConciliationSummaryModel(
  long erpSalesQuantity,
  BigDecimal erpGrossAmount,
  long acquirerSalesQuantity,
  BigDecimal acquirerGrossAmount,
  long matchedSalesQuantity,
  BigDecimal matchedAmount,
  long pendingSalesQuantity,
  BigDecimal pendingAmount,
  BigDecimal feeAmount,
  BigDecimal expectedFeeAmount,
  BigDecimal feeDifferenceAmount,
  BigDecimal bankSettledAmount,
  BigDecimal bankPendingAmount,
  BigDecimal debitPendingAmount,
  BigDecimal chargebackOpenAmount,
  long divergenceQuantity,
  BigDecimal divergenceAmount
) {}
