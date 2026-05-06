package com.cardsync.bff.controller.v1.representation.model.erp;

import java.math.BigDecimal;

public record TransactionErpSalesTotalsModel(
  BigDecimal totalGrossValue,
  BigDecimal totalFeeValue,
  BigDecimal totalNetValue,
  BigDecimal totalAdjustments,
  long quantity
) {}
