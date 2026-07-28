package com.cardsync.core.reconciliation;

public record PreImplantationDivergenceApplyResult(
  int analyzed,
  int linked,
  int skippedNegativeDifference,
  int skippedNoCandidates
) {}
