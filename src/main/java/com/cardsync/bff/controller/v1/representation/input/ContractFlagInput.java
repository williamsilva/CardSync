package com.cardsync.bff.controller.v1.representation.input;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record ContractFlagInput(
  @NotNull UUID flagId,
  @Valid List<ContractRateInput> contractRates
) {}
