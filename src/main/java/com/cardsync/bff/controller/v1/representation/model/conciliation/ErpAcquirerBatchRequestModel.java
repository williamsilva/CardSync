package com.cardsync.bff.controller.v1.representation.model.conciliation;

import java.util.List;
import java.util.UUID;

public record ErpAcquirerBatchRequestModel(
  List<UUID> transactionIds
) {
}
