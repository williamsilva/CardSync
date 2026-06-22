package com.cardsync.bff.controller.v1.representation.model.conciliation;

public record ReconciliationSettingsModel(
  int erpAcquirerPreviousDaysLookback,
  int erpAcquirerFutureDaysLookback,
  int reconciliationLookbackMonths
) {}
