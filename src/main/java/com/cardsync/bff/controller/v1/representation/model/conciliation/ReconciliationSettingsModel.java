package com.cardsync.bff.controller.v1.representation.model.conciliation;

import java.math.BigDecimal;

public record ReconciliationSettingsModel(
  int erpAcquirerPreviousDaysLookback,
  int erpAcquirerFutureDaysLookback,
  int reconciliationLookbackMonths,
  int creditOrderPendingDays,
  // Flags de reprocessamento — ordem = esteira de conciliação
  boolean reprocessErpAcquirerSales,
  boolean reprocessSalesSummaryTransactions,
  boolean reprocessAcquirerSaleCancellations,
  boolean reprocessErpAcquirerFees,
  boolean reprocessAcquirerSaleSummary,
  boolean reprocessSalesSummaryCreditOrder,
  boolean reprocessBankAcquirer,
  // Parâmetros de tolerância
  int dateToleranceDays,
  BigDecimal valueTolerance,
  int bankMarkNotReconciledAfterDays
) {}
