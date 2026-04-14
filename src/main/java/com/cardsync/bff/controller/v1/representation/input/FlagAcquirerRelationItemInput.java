package com.cardsync.bff.controller.v1.representation.input;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record FlagAcquirerRelationItemInput(
  @NotNull UUID acquirerId,
  @NotBlank String acquirerCode
) {}