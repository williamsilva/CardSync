package com.cardsync.bff.controller.v1.representation.input;

import com.cardsync.domain.model.enums.StatusEnum;
import com.cardsync.domain.model.enums.TypeCompanyEnum;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AcquirerInput(
  @NotBlank String cnpj,
  @NotBlank String fantasyName,
  @NotBlank String socialReason,
  StatusEnum status
) {}
