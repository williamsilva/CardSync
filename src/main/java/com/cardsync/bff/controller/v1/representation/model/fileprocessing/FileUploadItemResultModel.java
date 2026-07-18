package com.cardsync.bff.controller.v1.representation.model.fileprocessing;

public record FileUploadItemResultModel(
  String fileName,
  boolean success,
  String message
) {
}
