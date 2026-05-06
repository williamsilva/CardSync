package com.cardsync.bff.controller.v1.representation.model.fileprocessing.dashboard;

import java.util.UUID;

public record FileProcessingTopErrorFileModel(
  UUID processedFileId,
  String fileName,
  String origin,
  String group,
  String status,
  Long errors,
  Long warnings
) {}
