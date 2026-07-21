package com.cardsync.core.reconciliation.summary;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class AcquirerSaleSummaryStats {

  private UUID salesSummaryId;

  /** Total de transações vinculadas ao resumo. */
  private Long totalTransactions;

  /**
   * Transações ignoradas na análise: CANCELED + DELETED — mesmo critério de
   * {@link SalesSummaryTransactionStats#getExcludedTransactions()} (Etapa 1b). Excluídas do
   * total válido, NÃO contam como "elegível": um resumo só com canceladas/deletadas e pendentes
   * (nenhuma de fato conciliada) não deve virar PARTIALLY_RECONCILED.
   */
  private Long excludedTransactions;

  /** Transações elegíveis como conciliadas: AUTOMATICALLY_RECONCILED + MANUALLY_RECONCILED. */
  private Long eligibleTransactions;

  public int totalTransactionsAsInt() {
    return totalTransactions != null ? Math.toIntExact(totalTransactions) : 0;
  }

  public long excludedAsLong() {
    return excludedTransactions != null ? excludedTransactions : 0L;
  }

  public int eligibleTransactionsAsInt() {
    return eligibleTransactions != null ? Math.toIntExact(eligibleTransactions) : 0;
  }

  public boolean hasNoEligibleTransactions() {
    return eligibleTransactionsAsInt() == 0;
  }

  /** Transações que contam para a conciliação (total - canceladas/deletadas). */
  public long validCount() {
    return Math.max(0L, totalTransactionsAsInt() - excludedAsLong());
  }

  /** Todas as transações são CANCELED ou DELETED — nada a conciliar. */
  public boolean isAllExcluded() {
    return totalTransactionsAsInt() > 0 && totalTransactionsAsInt() == excludedAsLong();
  }

  public boolean isFullyEligible() {
    long valid = validCount();
    return valid > 0 && eligibleTransactionsAsInt() == valid;
  }

  public boolean isPartiallyEligible() {
    long valid = validCount();
    return valid > 0 && eligibleTransactionsAsInt() > 0 && eligibleTransactionsAsInt() < valid;
  }
}
