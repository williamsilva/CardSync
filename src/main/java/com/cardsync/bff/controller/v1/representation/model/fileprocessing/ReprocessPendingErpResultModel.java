package com.cardsync.bff.controller.v1.representation.model.fileprocessing;

public record ReprocessPendingErpResultModel(
  int scanned,
  int reprocessed,
  int resolved,
  int stillPendingContract,
  int stillPendingBusinessContext,
  int errors
) {
}
