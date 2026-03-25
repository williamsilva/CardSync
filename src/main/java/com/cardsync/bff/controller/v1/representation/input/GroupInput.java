package com.cardsync.bff.controller.v1.representation.input;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GroupInput(
  @NotBlank @Size(min = 3, max = 120) String name,
  @NotBlank @Size(min = 3, max = 1204) String description
) {}
