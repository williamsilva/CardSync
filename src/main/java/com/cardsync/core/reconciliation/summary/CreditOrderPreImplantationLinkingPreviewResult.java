package com.cardsync.core.reconciliation.summary;

import java.util.List;

public record CreditOrderPreImplantationLinkingPreviewResult(
  int analyzed,
  int exactMatch,
  int pvMismatch,
  int noMatch,
  List<CreditOrderPreImplantationLinkingCandidate> candidates,
  List<CreditOrderPreImplantationPvMismatch> mismatches
) {}
