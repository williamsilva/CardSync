package com.cardsync.bff.controller.v1.representation.input;

import com.cardsync.domain.model.enums.StatusEnum;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record FlagInput(
  @NotBlank String name,
  @NotNull Integer erpCode,
  @NotNull StatusEnum status,

  List<UUID> companyIds,
  List<UUID> acquirerIds
) {}