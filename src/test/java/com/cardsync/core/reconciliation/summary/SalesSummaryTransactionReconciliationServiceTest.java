package com.cardsync.core.reconciliation.summary;

import com.cardsync.core.conciliation.ReconciliationSettingsService;
import com.cardsync.core.config.ImplantationDateProvider;
import com.cardsync.domain.model.enums.StatusReconciliationEnum;
import com.cardsync.domain.repository.SalesSummaryRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cobre o achado de análise profunda da Etapa 2 (2026-09-12): recalculateForSalesSummaryIds
 * (recálculo pontual, chamado após ações manuais - ver ErpAcquirerResolutionService,
 * ConciliationWaitingService) não tratava resumos sem NENHUMA TransactionAcq vinculada, diferente
 * do batch principal (reconcile), que tem markSummariesWithoutTransactionsAsReconciled dedicado.
 * Não alcançável pelos chamadores atuais (todos passam um resumo que já tem pelo menos a
 * transação que disparou o recálculo), mas corrigido como defesa contra um chamador futuro.
 */
class SalesSummaryTransactionReconciliationServiceTest {

  private final ImplantationDateProvider implantationDateProvider = mock(ImplantationDateProvider.class);
  private final ReconciliationSettingsService reconciliationSettingsService = mock(ReconciliationSettingsService.class);
  private final SalesSummaryRepository salesSummaryRepository = mock(SalesSummaryRepository.class);

  private final SalesSummaryTransactionReconciliationService service = new SalesSummaryTransactionReconciliationService(
    implantationDateProvider, reconciliationSettingsService, salesSummaryRepository
  );

  @Test
  void recalculateMarksSummaryWithoutAnyTransactionAsReconciled() {
    UUID summaryWithTransaction = UUID.randomUUID();
    UUID summaryWithoutAnyTransaction = UUID.randomUUID();

    // A query é um INNER JOIN a partir de TransactionAcqEntity - um resumo sem nenhuma transação
    // simplesmente não aparece no resultado.
    when(salesSummaryRepository.findStatsForSalesSummaryTransactionReconciliationByIds(anyCollection(), anyCollection(), anyCollection()))
      .thenReturn(List.of(new SalesSummaryTransactionStats(summaryWithTransaction, 1L, 0L, 1L)));

    service.recalculateForSalesSummaryIds(List.of(summaryWithTransaction, summaryWithoutAnyTransaction));

    verify(salesSummaryRepository, times(1)).updateTransactionsStatusByIds(
      argThat((List<UUID> ids) -> ids != null && ids.contains(summaryWithTransaction) && ids.contains(summaryWithoutAnyTransaction)),
      eq(StatusReconciliationEnum.RECONCILED.getCode())
    );
  }
}
