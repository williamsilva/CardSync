package com.cardsync.bff.controller.v1.representation.input;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ManualCreditOrderLinkInput(
  @NotNull UUID creditOrderId,
  @NotNull UUID salesSummaryId
) {
}
