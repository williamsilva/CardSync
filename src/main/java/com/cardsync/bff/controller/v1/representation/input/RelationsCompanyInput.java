package com.cardsync.bff.controller.v1.representation.input;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public record RelationsCompanyInput(
  @NotEmpty List<UUID> companyIds
) {}