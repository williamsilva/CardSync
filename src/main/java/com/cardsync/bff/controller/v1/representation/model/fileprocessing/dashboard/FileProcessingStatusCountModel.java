package com.cardsync.bff.controller.v1.representation.model.fileprocessing.dashboard;

public record FileProcessingStatusCountModel(
  String group,
  String status,
  Long quantity
) {}
