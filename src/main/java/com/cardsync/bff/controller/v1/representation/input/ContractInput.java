package com.cardsync.bff.controller.v1.representation.input;

import com.cardsync.domain.model.enums.StatusEnum;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ContractInput(
  @NotBlank String description,
  @NotNull LocalDate startDate,
  LocalDate endDate,
  UUID companyId,
  @NotNull UUID acquirerId,
  UUID establishmentId,
  StatusEnum status,
  @Valid List<ContractFlagInput> contractFlags
) {}
