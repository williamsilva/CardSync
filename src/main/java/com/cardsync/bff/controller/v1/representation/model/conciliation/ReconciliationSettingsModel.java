package com.cardsync.bff.controller.v1.representation.model.conciliation;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ReconciliationSettingsModel(
  int erpAcquirerPreviousDaysLookback,
  int erpAcquirerFutureDaysLookback,
  int reconciliationLookbackMonths,
  int creditOrderPendingDays,
  // Flags de habilitação de etapas — ordem = esteira de conciliação
  boolean enabledErpAcquirer,
  boolean enabledSalesSummaryTransactions,
  boolean enabledAcquirerSaleCancellations,
  boolean enabledErpAcquirerFees,
  boolean enabledAcquirerSaleSummary,
  boolean enabledSalesSummaryCreditOrder,
  boolean enabledBankAcquirer,
  // Flags de reprocessamento — ordem = esteira de conciliação
  boolean reprocessErpAcquirerSales,
  boolean reprocessSalesSummaryTransactions,
  boolean reprocessAcquirerSaleCancellations,
  boolean reprocessErpAcquirerFees,
  boolean reprocessAcquirerSaleSummary,
  boolean reprocessSalesSummaryCreditOrder,
  boolean reprocessBankAcquirer,
  // Parâmetros de tolerância
  int dateToleranceDaysBefore,
  int dateToleranceDaysAfter,
  BigDecimal valueTolerance,
  int bankMarkNotReconciledAfterDays,
  /**
   * Teto de centavos para o subset-sum por programação dinâmica na conciliação Banco x Ordem de
   * Crédito/Parcela (Etapa 7). Acima dele, o subconjunto não é tentado e o lançamento fica
   * pendente mesmo com ordens compatíveis disponíveis.
   */
  long subsetDpMaxCents,
  // Rigidez do matching Banco x Ordem de Crédito / Parcela (Etapa 7) — default false =
  // comportamento legado (campo opcional/coringa quando nulo/desconhecido em qualquer lado)
  boolean flagMatchRequired,
  boolean establishmentMatchRequired,
  boolean paymentKindMatchRequired,
  // Implantação e marcação de lançamentos como legado
  LocalDate goLiveDate,
  int legacyMarkingMonths,
  /**
   * Calculado: go-live + meses. Lançamentos bancários com data de lançamento até
   * esta data (inclusive) podem ser marcados como legado; posteriores, não.
   */
  LocalDate legacyMarkingCutoffDate
) {}
