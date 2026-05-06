package com.cardsync.bff.controller.v1.representation.model.conciliation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ChargebackAnalysisModel(
  UUID id,
  LocalDate saleDate,
  LocalDate disputeDate,
  LocalDate dueDate,
  String company,
  String establishment,
  String acquirer,
  String flag,
  Long nsu,
  String authorization,
  String tid,
  BigDecimal saleValue,
  BigDecimal disputedValue,
  String reasonCode,
  String reasonDescription,
  String status
) {}
