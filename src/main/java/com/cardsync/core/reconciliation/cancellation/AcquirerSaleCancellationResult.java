package com.cardsync.core.reconciliation.cancellation;

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
public class AcquirerSaleCancellationResult {

  private FinancialReconciliationTriggerType trigger;

  private int adjustmentsAnalyzed;
  private int fullCancellationsIdentified;
  private int acquirerSalesCanceled;
  private int erpSalesCanceled;
  private int acquirerInstallmentsCanceled;
  private int erpInstallmentsCanceled;
  private int skippedPartialCancellations;
  private int skippedWithoutTransaction;
  private int skippedAlreadyCanceled;

  private OffsetDateTime startedAt;
  private OffsetDateTime finishedAt;
}
