package com.cardsync.bff.controller.v1.representation.model.transactions;

import java.math.BigDecimal;

public record TransactionTotalsModel(
  BigDecimal totalGrossValue,
  BigDecimal totalFeeValue,
  BigDecimal totalNetValue,
  BigDecimal totalAdjustments,
  long quantity
) {}
