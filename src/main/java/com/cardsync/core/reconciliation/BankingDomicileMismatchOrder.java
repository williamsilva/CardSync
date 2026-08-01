package com.cardsync.core.reconciliation;

import java.util.UUID;

/** Uma ordem de crédito candidata só encontrada ignorando banco (ver BankingDomicileDivergenceService). */
public record BankingDomicileMismatchOrder(
  UUID creditOrderId,
  Integer rvNumber,
  UUID salesSummaryId,
  String currentBankName,
  UUID suggestedBankingDomicileId
) {}
