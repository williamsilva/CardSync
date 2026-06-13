package com.cardsync.bff.controller.v1.representation.model.fileprocessing;

public record ImportedFileDayGroupStatusModel(
  ImportedFileGroupStatusModel erp,
  ImportedFileGroupStatusModel adq,
  ImportedFileGroupStatusModel bank
) {
}
