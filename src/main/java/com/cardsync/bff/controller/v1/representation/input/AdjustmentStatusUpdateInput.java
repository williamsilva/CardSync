package com.cardsync.bff.controller.v1.representation.input;

import com.cardsync.domain.model.enums.AdjustmentStatusEnum;
import jakarta.validation.constraints.NotNull;

public record AdjustmentStatusUpdateInput(
  @NotNull AdjustmentStatusEnum status
) {}
