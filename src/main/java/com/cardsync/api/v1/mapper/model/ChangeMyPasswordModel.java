package com.cardsync.api.v1.mapper.model;

import jakarta.validation.constraints.NotBlank;

public record ChangeMyPasswordModel(
  @NotBlank(message = "error.validation") String currentPassword,
  @NotBlank(message = "error.validation") String newPassword,
  @NotBlank(message = "error.validation") String confirmPassword
) {}