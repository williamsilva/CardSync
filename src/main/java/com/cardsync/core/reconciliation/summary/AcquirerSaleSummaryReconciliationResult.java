package com.cardsync.core.reconciliation.summary;

import com.cardsync.domain.model.enums.FinancialReconciliationTriggerType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class AcquirerSaleSummaryReconciliationResult {

  private FinancialReconciliationTriggerType trigger;
  private int summariesAnalyzed;
  private int summariesReconciled;
  private int summariesPartiallyReconciled;
  private int summariesPending;
  private int summariesBlockedByPreviousStep;
  private int transactionsAnalyzed;
  private int transactionsEligible;
  private int summariesWithoutTransactions;
  private OffsetDateTime startedAt;
  private OffsetDateTime finishedAt;
}
