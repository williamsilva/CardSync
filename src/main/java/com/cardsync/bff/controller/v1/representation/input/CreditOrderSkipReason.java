package com.cardsync.bff.controller.v1.representation.input;

public record CreditOrderSkipReason(
  String rvNumber,
  String code,
  int installmentTotal
) {}
