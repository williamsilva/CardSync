package com.cardsync.core.reconciliation.summary;

import com.cardsync.domain.model.enums.FinancialReconciliationTriggerType;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;

@Getter
@Builder(toBuilder = true)
public class SalesSummaryTransactionReconciliationResult {

  private FinancialReconciliationTriggerType trigger;
  private int summariesAnalyzed;
  private int summariesReconciled;
  private int summariesPartiallyReconciled;
  private int summariesPending;
  private int summariesAllExcluded;
  private int summariesWithoutTransactions;
  private OffsetDateTime startedAt;
  private OffsetDateTime finishedAt;
}
