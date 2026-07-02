package com.cardsync.bff.controller.v1.representation.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EmailSettingsRequest(
  @NotBlank String impl,
  @NotBlank @Size(max = 255) String fromName,
  @NotBlank @Size(max = 255) String fromEmail,
  @Size(max = 500) String brevoApiKey,
  @Size(max = 255) String brevoBaseUrl
) {}
