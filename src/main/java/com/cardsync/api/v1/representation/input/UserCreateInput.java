package com.cardsync.api.v1.representation.input;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.Set;
import java.util.UUID;

public record UserCreateInput(
  @NotBlank
  @Email
  String userName,

  @NotBlank
  String name,

  @NotBlank
  String document,

  Set<UUID> groupIds
) {}
