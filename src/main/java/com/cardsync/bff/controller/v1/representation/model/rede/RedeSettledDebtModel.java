package com.cardsync.bff.controller.v1.representation.model.rede;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record RedeSettledDebtModel(
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
  LocalDate liquidatedDate,
  BigDecimal valueDebitOrder,
  BigDecimal liquidatedValue,
  Integer reasonCode,
  String reasonDescription,
  String acquirer,
  String flag
) {}
