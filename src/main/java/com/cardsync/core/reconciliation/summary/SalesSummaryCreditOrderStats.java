package com.cardsync.core.reconciliation.summary;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class SalesSummaryCreditOrderStats {

  private UUID salesSummaryId;
  private Long creditOrdersCount;
  private BigDecimal grossValue;
  private Integer installmentTotal;

  public long creditOrdersCountSafe() {
    return creditOrdersCount == null ? 0L : creditOrdersCount;
  }

  public long installmentTotalSafe() {
    return installmentTotal == null || installmentTotal < 1 ? 1L : installmentTotal.longValue();
  }

  public boolean hasCreditOrders() {
    return creditOrdersCountSafe() > 0L;
  }

  public boolean isFullyReconciled() {
    return creditOrdersCountSafe() >= installmentTotalSafe();
  }
}
