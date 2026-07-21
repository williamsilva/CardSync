package com.cardsync.core.reconciliation.summary;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cobre a correção da divergência entre Etapa 2 (SalesSummaryTransactionReconciliationService) e
 * Etapa 5 (AcquirerSaleSummaryReconciliationService): antes, CANCELED/DELETED contavam como
 * "elegível" sem sair do total, então um resumo só com canceladas+pendentes (nenhuma de fato
 * conciliada) virava PARTIALLY_RECONCILED — sobrescrevendo de volta a classificação correta
 * (PENDING) que a Etapa 2 já tinha calculado e liberando indevidamente a Etapa 6.
 */
class AcquirerSaleSummaryStatsTest {

  @Test
  void cancelledAndPendingWithNoneReconciledIsNeitherFullyNorPartiallyEligible() {
    // 5 CANCELED (excluded) + 5 PENDING (nem excluído nem elegível) — zero de fato conciliado.
    AcquirerSaleSummaryStats stats = new AcquirerSaleSummaryStats(UUID.randomUUID(), 10L, 5L, 0L);

    assertThat(stats.isAllExcluded()).isFalse();
    assertThat(stats.isFullyEligible()).isFalse();
    assertThat(stats.isPartiallyEligible()).isFalse();
  }

  @Test
  void allTransactionsExcludedIsAllExcluded() {
    AcquirerSaleSummaryStats stats = new AcquirerSaleSummaryStats(UUID.randomUUID(), 5L, 5L, 0L);

    assertThat(stats.isAllExcluded()).isTrue();
    assertThat(stats.isFullyEligible()).isFalse();
  }

  @Test
  void allValidTransactionsReconciledIsFullyEligible() {
    // 2 CANCELED (excluídas) + 3 reconciliadas = válido 3, elegível 3.
    AcquirerSaleSummaryStats stats = new AcquirerSaleSummaryStats(UUID.randomUUID(), 5L, 2L, 3L);

    assertThat(stats.isFullyEligible()).isTrue();
    assertThat(stats.isPartiallyEligible()).isFalse();
  }

  @Test
  void someValidTransactionsReconciledIsPartiallyEligible() {
    // 2 CANCELED (excluídas) + 2 reconciliadas de 3 válidas.
    AcquirerSaleSummaryStats stats = new AcquirerSaleSummaryStats(UUID.randomUUID(), 5L, 2L, 2L);

    assertThat(stats.isFullyEligible()).isFalse();
    assertThat(stats.isPartiallyEligible()).isTrue();
  }
}
