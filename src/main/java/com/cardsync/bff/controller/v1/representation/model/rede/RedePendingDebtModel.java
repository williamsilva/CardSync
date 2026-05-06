package com.cardsync.bff.controller.v1.representation.model.rede;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record RedePendingDebtModel(
  UUID id,
  String processedFile,
  Integer lineNumber,
  String recordType,
  Integer pvNumber,
  Long nsu,
  String authorization,
  String tid,
  Long numberDebitOrder,
  LocalDate dateDebitOrder,
  BigDecimal valueDebitOrder,
  BigDecimal compensatedValue,
  Integer reasonCode,
  String reasonDescription,
  String acquirer,
  String flag,
  String company,
  String establishment
) {}
