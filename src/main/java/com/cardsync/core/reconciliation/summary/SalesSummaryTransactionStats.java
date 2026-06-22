package com.cardsync.core.reconciliation.summary;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SalesSummaryTransactionStats {

  private UUID salesSummaryId;

  /** Total de transações vinculadas ao resumo. */
  private Long totalTransactions;

  /** Transações ignoradas na análise: CANCELED + DELETED. */
  private Long excludedTransactions;

  /** Transações elegíveis como conciliadas: AUTOMATICALLY_RECONCILED + MANUALLY_RECONCILED. */
  private Long reconciledTransactions;

  public long totalAsLong() {
    return totalTransactions != null ? totalTransactions : 0L;
  }

  public long excludedAsLong() {
    return excludedTransactions != null ? excludedTransactions : 0L;
  }

  public long reconciledAsLong() {
    return reconciledTransactions != null ? reconciledTransactions : 0L;
  }

  /** Transações que contam para a conciliação (total - canceladas/deletadas). */
  public long validCount() {
    return Math.max(0L, totalAsLong() - excludedAsLong());
  }

  /** Todas as transações são CANCELED ou DELETED — nada a conciliar. */
  public boolean isAllExcluded() {
    return totalAsLong() > 0 && totalAsLong() == excludedAsLong();
  }

  /** Todas as transações válidas estão conciliadas. */
  public boolean isFullyReconciled() {
    long valid = validCount();
    return valid > 0 && reconciledAsLong() == valid;
  }

  /** Algumas transações válidas estão conciliadas, mas não todas. */
  public boolean isPartiallyReconciled() {
    long valid = validCount();
    return valid > 0 && reconciledAsLong() > 0 && reconciledAsLong() < valid;
  }
}
