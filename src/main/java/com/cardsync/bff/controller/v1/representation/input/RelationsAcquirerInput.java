package com.cardsync.bff.controller.v1.representation.input;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record RelationsAcquirerInput(
  @Valid @NotEmpty List<FlagAcquirerRelationItemInput> items
) {}