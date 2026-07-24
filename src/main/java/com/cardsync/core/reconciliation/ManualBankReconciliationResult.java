package com.cardsync.core.reconciliation;

import java.math.BigDecimal;

public record ManualBankReconciliationResult(
  int reconciled,
  int alreadyReconciled,
  int zeroValueReconciled,
  /** Diferença aceita entre o valor do lançamento e a soma das ordens vinculadas, se houver. */
  BigDecimal divergenceValue
) {}
