package com.cardsync.bff.controller.v1.representation.model.conciliation;

import java.math.BigDecimal;

public record ConciliationAgingModel(
  String bucket,
  long quantity,
  BigDecimal amount,
  String type
) {}
