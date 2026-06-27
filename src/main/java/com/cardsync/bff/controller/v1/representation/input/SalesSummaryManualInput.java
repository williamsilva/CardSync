package com.cardsync.bff.controller.v1.representation.input;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record SalesSummaryManualInput(
  @NotNull Integer pvNumber,
  @NotNull String acquirerId,
  String companyId,
  @NotNull Integer rvNumber,
  @NotNull LocalDate rvDate,
  @NotNull @Positive BigDecimal grossValue,
  BigDecimal discountValue,
  BigDecimal liquidValue,
  BigDecimal tipValue,
  BigDecimal rejectedValue,
  BigDecimal adjustedValue,
  Integer numberCvNsu,
  LocalDate firstInstallmentCreditDate,
  String summaryType,
  @Valid List<TransactionInput> transactions
) {

  public record TransactionInput(
    Long nsu,
    String cardNumber,
    String authorization,
    String referenceNumber,
    BigDecimal grossValue,
    BigDecimal discountValue,
    BigDecimal liquidValue,
    BigDecimal tipValue,
    LocalDateTime saleDate,
    LocalDate creditDate,
    Integer installment,
    Integer modality,
    String flagName,
    String tid,
    Integer capture
  ) {}
}
