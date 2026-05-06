package com.cardsync.bff.controller.v1.representation.model.fileprocessing;

import com.cardsync.domain.model.enums.FileStatusEnum;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ProcessedFileSummaryModel(
  UUID id,
  String file,
  FileStatusEnum status,
  OffsetDateTime startedAt,
  OffsetDateTime finishedAt,
  Integer totalLines,
  Integer processedLines,
  Integer ignoredLines,
  Integer warningLines,
  Integer errorLines,
  Integer pendingContractLines,
  Integer pendingBusinessContextLines,
  String statusMessage,
  String errorMessage
) {
}
