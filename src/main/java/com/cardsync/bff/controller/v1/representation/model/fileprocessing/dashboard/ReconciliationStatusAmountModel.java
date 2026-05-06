package com.cardsync.bff.controller.v1.representation.model.fileprocessing.dashboard;

import java.math.BigDecimal;

public record ReconciliationStatusAmountModel(
  String source,
  Integer status,
  Long quantity,
  BigDecimal amount
) {}
