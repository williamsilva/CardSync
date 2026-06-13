package com.cardsync.core.reconciliation.summary;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor
public class SalesSummaryCreditOrderStats {

  private UUID salesSummaryId;
  private Long creditOrdersCount;

  public long creditOrdersCountSafe() {
    return creditOrdersCount == null ? 0L : creditOrdersCount;
  }

  public boolean hasCreditOrders() {
    return creditOrdersCountSafe() > 0L;
  }
}
