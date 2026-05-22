package com.cardsync.bff.controller.v1.representation.model.conciliation;

public record ErpAcquirerFieldDiffModel(
  String field,
  String erpValue,
  String acquirerValue,
  boolean different
) {
}
