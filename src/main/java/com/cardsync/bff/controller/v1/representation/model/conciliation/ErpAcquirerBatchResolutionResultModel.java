package com.cardsync.bff.controller.v1.representation.model.conciliation;

import java.util.List;
import java.util.UUID;

public record ErpAcquirerBatchResolutionResultModel(
  String action,
  int requested,
  int success,
  int failed,
  List<ErpAcquirerBatchResolutionItemModel> items
) {

  public record ErpAcquirerBatchResolutionItemModel(
    UUID sourceId,
    UUID erpId,
    UUID acquirerId,
    String status,
    String message
  ) {
  }
}
