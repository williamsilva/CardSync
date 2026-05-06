package com.cardsync.bff.controller.v1.representation.model.conciliation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record BankSettlementAnalysisModel(
  UUID id,
  String sourceType,
  LocalDate expectedDate,
  LocalDate settlementDate,
  String company,
  String establishment,
  String acquirer,
  String bank,
  String flag,
  String modality,
  Long creditOrderNumber,
  String releaseReference,
  BigDecimal expectedValue,
  BigDecimal settledValue,
  BigDecimal differenceValue,
  Long daysDifference,
  String status,
  String detail
) {}
