package com.cardsync.bff.controller.v1.representation.model.fileprocessing;

import java.time.OffsetDateTime;
import java.util.List;

public record ImportedFileEntityStatusModel(
  String name,
  int filesReceived,
  int expected,
  String status,
  String entityStatus,
  OffsetDateTime statusDate,
  List<String> missingFiles,
  List<String> presentFiles
) {
}
