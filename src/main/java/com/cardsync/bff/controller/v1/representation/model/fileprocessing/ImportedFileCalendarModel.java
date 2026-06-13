package com.cardsync.bff.controller.v1.representation.model.fileprocessing;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

public record ImportedFileCalendarModel(
  YearMonth month,
  LocalDate startDate,
  LocalDate endDate,
  int daysWithFiles,
  int daysWithoutFiles,
  int totalFiles,
  List<ImportedFileCalendarDayModel> days
) {
}
