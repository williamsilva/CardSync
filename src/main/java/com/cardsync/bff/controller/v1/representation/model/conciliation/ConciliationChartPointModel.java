package com.cardsync.bff.controller.v1.representation.model.conciliation;

import java.math.BigDecimal;

public record ConciliationChartPointModel(
  String label,
  BigDecimal value,
  Long quantity
) {}
