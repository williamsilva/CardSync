package com.cardsync.bff.controller.v1.representation.model.conciliation;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ReconciliationSettingsRequest(
  @Min(0) @Max(365) int erpAcquirerPreviousDaysLookback,
  @Min(0) @Max(365) int erpAcquirerFutureDaysLookback,
  @Min(1) @Max(120) int reconciliationLookbackMonths,
  @Min(1) @Max(365) int creditOrderPendingDays,
  // Flags de reprocessamento — ordem = esteira de conciliação
  boolean reprocessErpAcquirerSales,
  boolean reprocessSalesSummaryTransactions,
  boolean reprocessAcquirerSaleCancellations,
  boolean reprocessErpAcquirerFees,
  boolean reprocessAcquirerSaleSummary,
  boolean reprocessSalesSummaryCreditOrder,
  boolean reprocessBankAcquirer,
  // Parâmetros de tolerância
  @Min(0) @Max(60) int dateToleranceDays,
  @NotNull @DecimalMin("0.00") @DecimalMax("10.00") BigDecimal valueTolerance,
  @Min(0) @Max(60) int bankMarkNotReconciledAfterDays
) {}
