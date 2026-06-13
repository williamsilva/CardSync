package com.cardsync.bff.controller.v1.representation.model.fileprocessing;

import java.util.List;

public record ImportedFileGroupStatusModel(
  String status,
  int received,
  int expected,
  List<ImportedFileEntityStatusModel> entities
) {
}
