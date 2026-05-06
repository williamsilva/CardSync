package com.cardsync.bff.controller.v1.representation.model.fileprocessing;

import com.cardsync.domain.model.enums.ProcessedFileErrorTypeEnum;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ProcessedFileErrorModel(
  UUID id,
  Integer lineNumber,
  ProcessedFileErrorTypeEnum errorType,
  String errorCode,
  String message,
  String rawLine,
  OffsetDateTime createdAt
) {
}
