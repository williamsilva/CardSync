package com.cardsync.bff.controller.v1.representation.input;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AdjustmentManualInput(
  @NotNull Integer pvNumber,
  @NotNull String acquirerId,
  String companyId,
  Integer pvNumberOriginal,
  @NotNull Integer rvNumberOriginal,
  @NotNull LocalDate adjustmentDate,
  LocalDate creditDate,
  @NotNull @Positive BigDecimal adjustmentValue,
  BigDecimal transactionValue,
  BigDecimal totalDebitValue,
  BigDecimal pendingValue,
  String debitType,
  String adjustmentType,
  String adjustmentDescription,
  String flagName,
  String rawAdjustmentCode
) {}
