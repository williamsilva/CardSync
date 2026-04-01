package com.cardsync.bff.controller.v1.representation.input;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record ListIdsInput(
  @NotNull
  @NotEmpty
  List<UUID> ids
) {}
