package com.cardsync.bff.controller.v1.representation.model.conciliation;

public record ErpUpdateIdentityRequest(
  Long nsu,
  String authorization
) {
}
