package com.cardsync.bff.controller.v1.representation.input;

public record CreditOrderImportSkipReason(
  String fileName,
  int lineNumber,
  String rvNumber,
  Integer installmentNumber,
  String code
) {}
