package com.cardsync.core.reconciliation;

import java.util.List;

public record BankingDomicileDivergencePreviewResult(
  int releasesAnalyzed,
  int candidatesFound,
  List<BankingDomicileDivergenceCandidate> candidates
) {}
