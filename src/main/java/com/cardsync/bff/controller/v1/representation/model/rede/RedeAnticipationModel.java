package com.cardsync.bff.controller.v1.representation.model.rede;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record RedeAnticipationModel(
  UUID id,
  String processedFile,
  Integer lineNumber,
  Integer pvNumber,
  Integer numberRvCorresponding,
  Integer installmentNumber,
  Integer installmentNumberMax,
  LocalDate releaseDate,
  LocalDate originalDueDate,
  LocalDate dateRvCorresponding,
  BigDecimal grossValue,
  BigDecimal releaseValue,
  BigDecimal discountRateValue,
  BigDecimal originalCreditValue,
  String acquirer,
  String flag,
  String company,
  String establishment
) {}
