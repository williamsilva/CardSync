package com.cardsync.bff.controller.v1.representation.model.fileprocessing;

import com.cardsync.domain.model.enums.FileGroupEnum;
import com.cardsync.domain.model.enums.FileStatusEnum;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ImportedFileCalendarItemModel(
  UUID id,
  String file,
  FileGroupEnum group,
  String category,
  String categoryLabel,
  String typeFile,
  String origin,
  FileStatusEnum status,
  LocalDate dateFile,
  OffsetDateTime dateImport
) {
}
