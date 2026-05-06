package com.cardsync.bff.controller.v1.representation.model.conciliation;

import java.math.BigDecimal;

public record ConciliationComparisonModel(
  BigDecimal erpAmount,
  BigDecimal acquirerAmount,
  BigDecimal differenceAmount,
  BigDecimal matchedAmount,
  BigDecimal pendingAmount
) {}
