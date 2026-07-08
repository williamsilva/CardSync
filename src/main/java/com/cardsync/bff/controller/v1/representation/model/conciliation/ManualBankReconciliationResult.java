package com.cardsync.bff.controller.v1.representation.model.conciliation;

public record ManualBankReconciliationResult(
  int reconciled,
  int alreadyReconciled
) {}
