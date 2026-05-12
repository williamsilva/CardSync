package com.cardsync.bff.controller.v1.representation.model.conciliation;

public record ReconcileErpAcquirerResultModel(
  int analyzed,
  int matched,
  int updated,
  int skippedDivergent,
  int flagUpdated,
  int businessContextUpdated,
  int notMatched,
  int valueDivergences,
  int acquirerDivergences,
  int ambiguousMatches
) {}
