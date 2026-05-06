package com.cardsync.bff.controller.v1.representation.model.fileprocessing.dashboard;

import java.math.BigDecimal;

public record FileProcessingDivergenceContextModel(
  String source,
  String company,
  String acquirer,
  String bank,
  String flag,
  Long quantity,
  BigDecimal amount
) {}
