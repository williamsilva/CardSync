package com.cardsync.bff.controller.v1.representation.model.conciliation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record DivergenceAnalysisModel(
  UUID id,
  String type,
  String severity,
  String status,
  String source,
  LocalDate referenceDate,
  String company,
  String establishment,
  String acquirer,
  String flag,
  String modality,
  String identifier,
  BigDecimal expectedValue,
  BigDecimal actualValue,
  BigDecimal differenceValue,
  String message,
  String actionHint,
  String fileName
) {}
