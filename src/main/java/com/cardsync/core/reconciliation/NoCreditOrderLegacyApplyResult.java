package com.cardsync.core.reconciliation;

public record NoCreditOrderLegacyApplyResult(
  int analyzed,
  int marked,
  int skippedHasCandidates,
  int skippedOutsideLegacyWindow
) {}
