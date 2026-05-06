package com.cardsync.bff.controller.v1.representation.model.rede;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record RedeAdjustmentModel(
  UUID id,
  String processedFile,
  Integer lineNumber,
  String recordType,
  String sourceRecordIdentifier,
  Boolean ecommerce,
  Integer pvNumber,
  Long nsu,
  String authorization,
  String tid,
  Integer adjustmentReason,
  String adjustmentDescription,
  LocalDate adjustmentDate,
  LocalDate creditDate,
  LocalDate releaseDate,
  BigDecimal adjustmentValue,
  BigDecimal grossValue,
  BigDecimal liquidValue,
  BigDecimal discountValue,
  String acquirer,
  String company,
  String establishment
) {}
