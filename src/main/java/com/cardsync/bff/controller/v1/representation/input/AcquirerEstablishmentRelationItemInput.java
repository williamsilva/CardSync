package com.cardsync.bff.controller.v1.representation.input;

import com.cardsync.domain.model.enums.StatusEnum;
import com.cardsync.domain.model.enums.TypeEstablishmentEnum;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AcquirerEstablishmentRelationItemInput(
  @NotNull UUID companyId,
  @NotNull Integer pvNumber,
  @NotNull StatusEnum status,
  @NotNull TypeEstablishmentEnum type
) {}