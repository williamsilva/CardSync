package com.cardsync.bff.controller.v1.representation.model.rede;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record RedeTotalizerModel(
  UUID id,
  String type,
  String processedFile,
  Integer lineNumber,
  Integer pvNumber,
  LocalDate creditDate,
  BigDecimal totalCreditValue,
  BigDecimal totalValueAdvanceCredits,
  Integer totalNumberMatrixSummaries,
  BigDecimal totalValueNormalCredits,
  BigDecimal totalValueAnticipated,
  Integer amountCreditAdjustments,
  BigDecimal totalValueCreditAdjustments,
  Integer amountDebitAdjustments,
  BigDecimal totalValueDebitAdjustments,
  String acquirer,
  String company,
  String establishment
) {}
