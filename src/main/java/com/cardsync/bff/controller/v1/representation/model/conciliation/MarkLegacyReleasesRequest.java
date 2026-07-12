package com.cardsync.bff.controller.v1.representation.model.conciliation;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record MarkLegacyReleasesRequest(
  @NotEmpty List<@NotNull UUID> releaseBankIds
) {}
