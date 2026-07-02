package com.cardsync.bff.controller.v1.representation.model.conciliation;

public record SchedulerSettingsModel(
  boolean enabled,
  boolean completePipelineEnabled,
  String completePipelineCron,
  boolean completePipelineStopOnStepError,
  boolean logIdleCycles
) {}
