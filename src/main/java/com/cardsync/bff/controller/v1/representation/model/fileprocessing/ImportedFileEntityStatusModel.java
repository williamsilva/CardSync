package com.cardsync.bff.controller.v1.representation.model.fileprocessing;

import java.time.OffsetDateTime;

public record ImportedFileEntityStatusModel(
  String name,
  int filesReceived,
  String status,
  String entityStatus,
  OffsetDateTime statusDate
) {
}
