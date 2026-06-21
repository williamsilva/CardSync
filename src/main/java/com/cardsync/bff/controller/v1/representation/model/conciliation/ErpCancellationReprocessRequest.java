package com.cardsync.bff.controller.v1.representation.model.conciliation;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record ErpCancellationReprocessRequest(
  @Min(2000) @Max(2100) int year,
  @Min(1) @Max(12) int month
) {
}
