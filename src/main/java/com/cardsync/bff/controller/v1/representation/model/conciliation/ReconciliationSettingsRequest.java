package com.cardsync.bff.controller.v1.representation.model.conciliation;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record ReconciliationSettingsRequest(
  @Min(0) @Max(365) int erpAcquirerPreviousDaysLookback,
  @Min(0) @Max(365) int erpAcquirerFutureDaysLookback,
  @Min(1) @Max(36) int reconciliationLookbackMonths
) {}
