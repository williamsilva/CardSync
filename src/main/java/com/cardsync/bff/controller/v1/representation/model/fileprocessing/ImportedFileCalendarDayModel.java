package com.cardsync.bff.controller.v1.representation.model.fileprocessing;

import java.time.LocalDate;
import java.util.List;

public record ImportedFileCalendarDayModel(
  LocalDate date,
  boolean hasFiles,
  boolean future,
  int totalFiles,
  int erpFiles,
  int adqFiles,
  int bankFiles,
  ImportedFileDayGroupStatusModel groupStatus,
  List<ImportedFileCalendarItemModel> files
) {
}
