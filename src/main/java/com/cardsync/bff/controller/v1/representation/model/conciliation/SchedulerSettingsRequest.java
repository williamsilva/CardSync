package com.cardsync.bff.controller.v1.representation.model.conciliation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SchedulerSettingsRequest(
  boolean enabled,
  boolean completePipelineEnabled,
  @NotBlank @Size(max = 100) String completePipelineCron,
  boolean completePipelineStopOnStepError,
  boolean logIdleCycles
) {}
