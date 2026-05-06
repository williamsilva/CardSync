package com.cardsync.bff.controller.v1.representation.model.conciliation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record DebitAnalysisModel(
  UUID id,
  LocalDate debitDate,
  LocalDate settlementDate,
  String company,
  String establishment,
  String acquirer,
  String flag,
  String type,
  String reasonCode,
  String reasonDescription,
  BigDecimal debitValue,
  BigDecimal settledValue,
  String status,
  String processedFile
) {}
