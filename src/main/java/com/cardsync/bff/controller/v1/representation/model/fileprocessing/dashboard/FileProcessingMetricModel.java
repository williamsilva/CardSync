package com.cardsync.bff.controller.v1.representation.model.fileprocessing.dashboard;

import java.math.BigDecimal;

public record FileProcessingMetricModel(
  String key,
  String label,
  Long quantity,
  BigDecimal amount
) {
  public static FileProcessingMetricModel of(String key, String label, Long quantity) {
    return new FileProcessingMetricModel(key, label, quantity, BigDecimal.ZERO);
  }

  public static FileProcessingMetricModel of(String key, String label, Long quantity, BigDecimal amount) {
    return new FileProcessingMetricModel(key, label, quantity, amount == null ? BigDecimal.ZERO : amount);
  }
}
