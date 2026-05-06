package com.cardsync.bff.controller.v1.representation.model.fileprocessing;

import com.cardsync.domain.model.enums.FileGroupEnum;
import com.cardsync.domain.model.enums.FileStatusEnum;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ProcessedFileModel(
  UUID id,
  String file,
  String origin,
  FileGroupEnum group,
  FileStatusEnum status,
  LocalDate dateFile,
  OffsetDateTime dateImport,
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
