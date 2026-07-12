package com.cardsync.core.reconciliation;

public record ManualBankReconciliationResult(
  int reconciled,
  int alreadyReconciled,
  int zeroValueReconciled
) {}
