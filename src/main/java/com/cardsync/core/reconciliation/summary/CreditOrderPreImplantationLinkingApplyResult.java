package com.cardsync.core.reconciliation.summary;

public record CreditOrderPreImplantationLinkingApplyResult(
  int analyzed,
  int linked,
  int pvMismatch,
  int noMatch
) {}
