package com.cardsync.core.reconciliation;

import java.util.List;

public record PreImplantationDivergencePreviewResult(
  int analyzed,
  int eligibleToLink,
  int skippedNegativeDifference,
  int skippedNoCandidates,
  List<PreImplantationDivergenceCandidate> candidates
) {}
