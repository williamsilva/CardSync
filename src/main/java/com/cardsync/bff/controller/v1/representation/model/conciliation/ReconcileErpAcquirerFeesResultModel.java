package com.cardsync.bff.controller.v1.representation.model.conciliation;

public record ReconcileErpAcquirerFeesResultModel(
  int analyzed,
  int updatedErpSales,
  int divergentRates,
  int missingValidContracts,
  int okRates,
  int skippedWithoutAcquirer
) {}
