package com.cardsync.bff.controller.v1.representation.model.rede;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record RedeCreditOrderModel(
  UUID id,
  String processedFile,
  Integer lineNumber,
  Long creditOrderNumber,
  Integer rvNumber,
  Integer originalPvNumber,
  Integer installmentNumber,
  Integer installmentTotal,
  LocalDate rvDate,
  LocalDate releaseDate,
  LocalDate creditOrderDate,
  BigDecimal releaseValue,
  BigDecimal grossRvValue,
  BigDecimal discountRateValue,
  String acquirer,
  String flag,
  String company
) {}
