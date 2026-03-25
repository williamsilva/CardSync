package com.cardsync.api.v1.representation.input;

import jakarta.validation.constraints.NotBlank;

public record ChangePasswordInput(@NotBlank String newPassword) {}
