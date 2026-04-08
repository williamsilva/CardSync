package com.cardsync.bff.controller.v1.representation.input;

import com.cardsync.domain.model.enums.StatusEnum;
import com.cardsync.domain.model.enums.TypeEstablishmentEnum;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record EstablishmentInput(
  @NotNull Integer pvNumber,
  @NotNull UUID companyId,
  @NotNull UUID acquirerId,
  @NotNull TypeEstablishmentEnum type,
  @NotNull StatusEnum status
) {}
