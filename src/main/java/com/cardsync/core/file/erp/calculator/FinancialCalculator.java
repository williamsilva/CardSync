package com.cardsync.core.file.erp.calculator;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class FinancialCalculator {
  private FinancialCalculator() { }

  public static BigDecimal calculateDiscountValue(BigDecimal grossValue, BigDecimal feePercent) {
    if (grossValue == null || feePercent == null) return BigDecimal.ZERO;
    return grossValue.multiply(feePercent).divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);
  }

  public static BigDecimal calculateNetValue(BigDecimal grossValue, BigDecimal feePercent) {
    if (grossValue == null) return BigDecimal.ZERO;
    return grossValue.subtract(calculateDiscountValue(grossValue, feePercent));
  }
}
