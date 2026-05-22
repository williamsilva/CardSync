package com.cardsync.bff.controller.v1.representation.model.conciliation;

import java.util.UUID;

public record ErpAcquirerResolutionResultModel(
  UUID erpId,
  UUID acquirerId,
  String action,
  String status,
  String message
) {
}
