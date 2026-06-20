package com.cardsync.bff.controller.v1.representation.model.transactions;

import java.math.BigDecimal;

public record ValueTotalsModel(
  BigDecimal totalValue,
  long quantity
) {}
