package com.cardsync.bff.controller.v1.representation.model.conciliation;

public record ErpCancellationReprocessResult(
  int year,
  int month,
  int acqSalesCancelled,
  int erpSalesCancelled,
  int erpInstallmentsCancelled,
  int erpLinkedBeforeCancel,
  int skippedAlreadyCancelled,
  int skippedNoErpLinked
) {
}
