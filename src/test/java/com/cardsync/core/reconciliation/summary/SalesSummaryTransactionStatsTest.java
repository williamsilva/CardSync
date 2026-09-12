package com.cardsync.core.reconciliation.summary;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cobre a classificação da Etapa 2 (SalesSummaryTransactionReconciliationService) - a
 * classe-gabarito cujo comportamento correto foi usado pra corrigir a divergência da Etapa 5
 * (ver AcquirerSaleSummaryStatsTest, classe irmã). Mesmos cenários espelhados aqui, já que até
 * agora só a Etapa 5 tinha teste unitário direto pra essa lógica de contagem.
 */
class SalesSummaryTransactionStatsTest {

  @Test
  void cancelledAndPendingWithNoneReconciledIsNeitherFullyNorPartiallyReconciled() {
    // 5 CANCELED/DELETED (excluídas) + 5 PENDING (nem excluída nem reconciliada) — zero de fato conciliado.
    SalesSummaryTransactionStats stats = new SalesSummaryTransactionStats(UUID.randomUUID(), 10L, 5L, 0L);

    assertThat(stats.isAllExcluded()).isFalse();
    assertThat(stats.isFullyReconciled()).isFalse();
    assertThat(stats.isPartiallyReconciled()).isFalse();
  }

  @Test
  void allTransactionsExcludedIsAllExcluded() {
    SalesSummaryTransactionStats stats = new SalesSummaryTransactionStats(UUID.randomUUID(), 5L, 5L, 0L);

    assertThat(stats.isAllExcluded()).isTrue();
    assertThat(stats.isFullyReconciled()).isFalse();
  }

  @Test
  void allValidTransactionsReconciledIsFullyReconciled() {
    // 2 CANCELED/DELETED (excluídas) + 3 reconciliadas (automática ou manualmente) = válido 3, reconciliado 3.
    SalesSummaryTransactionStats stats = new SalesSummaryTransactionStats(UUID.randomUUID(), 5L, 2L, 3L);

    assertThat(stats.isFullyReconciled()).isTrue();
    assertThat(stats.isPartiallyReconciled()).isFalse();
  }

  @Test
  void someValidTransactionsReconciledIsPartiallyReconciled() {
    // 2 CANCELED/DELETED (excluídas) + 2 reconciliadas de 3 válidas.
    SalesSummaryTransactionStats stats = new SalesSummaryTransactionStats(UUID.randomUUID(), 5L, 2L, 2L);

    assertThat(stats.isFullyReconciled()).isFalse();
    assertThat(stats.isPartiallyReconciled()).isTrue();
  }

  @Test
  void zeroTransactionsIsNeitherAllExcludedNorReconciled() {
    // Caso não deveria surgir das queries agregadas (INNER JOIN garante count >= 1), mas a classe
    // não deve quebrar/contar como "tudo excluído" ou "totalmente conciliado" num total zerado -
    // resumos sem NENHUMA transação são tratados à parte (markSummariesWithoutTransactionsAsReconciled).
    SalesSummaryTransactionStats stats = new SalesSummaryTransactionStats(UUID.randomUUID(), 0L, 0L, 0L);

    assertThat(stats.isAllExcluded()).isFalse();
    assertThat(stats.isFullyReconciled()).isFalse();
    assertThat(stats.isPartiallyReconciled()).isFalse();
  }
}
