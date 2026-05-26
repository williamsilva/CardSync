package com.cardsync.core.conciliation.analysis;

import com.cardsync.bff.controller.v1.representation.model.conciliation.ConciliationFeeAnalysisModel;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record FeeAnalysisResult(
  UUID id,
  OffsetDateTime saleDate,
  String company,
  String establishment,
  String acquirer,
  String flag,
  String modality,
  Long nsu,
  String authorization,
  BigDecimal grossValue,
  BigDecimal expectedRate,
  BigDecimal appliedRate,
  BigDecimal expectedFeeValue,
  BigDecimal appliedFeeValue,
  BigDecimal feeDifference,
  String status
) {
  public ConciliationFeeAnalysisModel toModel() {
    return new ConciliationFeeAnalysisModel(
      id, saleDate, company, establishment, acquirer, flag, modality, nsu, authorization,
      grossValue, expectedRate, appliedRate, expectedFeeValue, appliedFeeValue, feeDifference, status
    );
  }
}
