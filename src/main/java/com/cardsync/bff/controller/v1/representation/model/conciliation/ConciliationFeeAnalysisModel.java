package com.cardsync.bff.controller.v1.representation.model.conciliation;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ConciliationFeeAnalysisModel(
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
) {}
