package com.cardsync.core.reconciliation;

import java.util.List;

public record NoCreditOrderLegacyPreviewResult(
  int analyzed,
  int eligibleToMark,
  int skippedHasCandidates,
  int skippedOutsideLegacyWindow,
  List<NoCreditOrderLegacyCandidate> candidates
) {}
