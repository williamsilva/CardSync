package com.cardsync.bff.controller.v1.representation.input;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;
import java.util.UUID;

public record UserInput (
  @NotBlank @Email @Size(max = 120) String userName,
  @NotBlank @Size(min = 2, max = 120) String name,
  @NotBlank @Size(min = 11, max = 14) String document,
  @NotNull @Size(min = 1) Set<UUID> groupIds
) {}
