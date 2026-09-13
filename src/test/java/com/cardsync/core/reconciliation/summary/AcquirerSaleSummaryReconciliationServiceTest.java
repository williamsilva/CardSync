package com.cardsync.core.reconciliation.summary;

import com.cardsync.core.conciliation.ReconciliationSettingsService;
import com.cardsync.core.config.ImplantationDateProvider;
import com.cardsync.domain.model.enums.FinancialReconciliationTriggerType;
import com.cardsync.domain.model.enums.StatusReconciliationEnum;
import com.cardsync.domain.repository.SalesSummaryRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cobre o achado de análise profunda da Etapa 5 (2026-09-12): {@code AcquirerSaleSummaryReconciliationService}
 * não tinha nenhum teste dedicado (só a classe de stats, em {@link AcquirerSaleSummaryStatsTest}).
 * Também documenta o achado de que {@code reconciliationSettingsService.isReprocessAcquirerSaleSummary()}
 * é lido, mas não é repassado a nenhuma consulta - hoje o comportamento real é sempre reavaliar
 * todo summary com transação vinculada no lookback, independente do valor configurado (decisão
 * deliberada de design: esta etapa roda depois de Cancelamentos/Taxas no mesmo pipeline e precisa
 * reavaliar tudo para capturar mudanças de status feitas por elas). As chamadas ao repositório
 * verificadas abaixo usam a assinatura real do método (sem parâmetro de reprocess), travando esse
 * comportamento como teste de regressão.
 */
class AcquirerSaleSummaryReconciliationServiceTest {

  private final ImplantationDateProvider implantationDateProvider = mock(ImplantationDateProvider.class);
  private final ReconciliationSettingsService reconciliationSettingsService = mock(ReconciliationSettingsService.class);
  private final SalesSummaryRepository salesSummaryRepository = mock(SalesSummaryRepository.class);

  private final AcquirerSaleSummaryReconciliationService service = new AcquirerSaleSummaryReconciliationService(
    implantationDateProvider, reconciliationSettingsService, salesSummaryRepository
  );

  @Test
  void classifiesSummariesAndUpdatesEachStatusBucketSeparately() {
    UUID allExcluded = UUID.randomUUID();
    UUID fullyEligible = UUID.randomUUID();
    UUID partiallyEligible = UUID.randomUUID();
    UUID stillPending = UUID.randomUUID();

    when(implantationDateProvider.get()).thenReturn(LocalDate.of(2025, 1, 1));
    when(reconciliationSettingsService.getReconciliationLookbackMonths()).thenReturn(1);
    when(salesSummaryRepository.findStatsForAcquirerSaleSummaryReconciliation(any(), any(), any(), any()))
      .thenReturn(List.of(
        new AcquirerSaleSummaryStats(allExcluded, 2L, 2L, 0L),
        new AcquirerSaleSummaryStats(fullyEligible, 2L, 0L, 2L),
        new AcquirerSaleSummaryStats(partiallyEligible, 3L, 0L, 1L),
        new AcquirerSaleSummaryStats(stillPending, 2L, 0L, 0L)
      ));

    service.reconcilePending(FinancialReconciliationTriggerType.MANUAL);

    verify(salesSummaryRepository, times(1)).updateTransactionsStatusByIds(
      argThat((List<UUID> ids) -> ids.containsAll(List.of(allExcluded, fullyEligible)) && ids.size() == 2),
      eq(StatusReconciliationEnum.RECONCILED.getCode())
    );
    verify(salesSummaryRepository, times(1)).updateTransactionsStatusByIds(
      argThat((List<UUID> ids) -> ids.equals(List.of(partiallyEligible))),
      eq(StatusReconciliationEnum.PARTIALLY_RECONCILED.getCode())
    );
    verify(salesSummaryRepository, times(1)).updateTransactionsStatusByIds(
      argThat((List<UUID> ids) -> ids.equals(List.of(stillPending))),
      eq(StatusReconciliationEnum.PENDING.getCode())
    );
  }

  @Test
  void ignoreLookbackUsesTheBackfillQueryInsteadOfTheLookbackOne() {
    when(implantationDateProvider.get()).thenReturn(LocalDate.of(2025, 1, 1));
    when(salesSummaryRepository.findStatsForAcquirerSaleSummaryReconciliationIgnoringLookback(any(), any(), any()))
      .thenReturn(List.of());

    service.reconcilePending(FinancialReconciliationTriggerType.MANUAL, true);

    verify(salesSummaryRepository, times(1))
      .findStatsForAcquirerSaleSummaryReconciliationIgnoringLookback(any(), any(), any());
    verify(salesSummaryRepository, never())
      .findStatsForAcquirerSaleSummaryReconciliation(any(), any(), any(), any());
  }

  @Test
  void reprocessFlagHasNoEffectOnWhichSummariesAreReevaluated() {
    // Achado da Etapa 5: isReprocessAcquirerSaleSummary() é lido só para log - a query real
    // (findStatsForAcquirerSaleSummaryReconciliation) não recebe esse valor e sempre reavalia
    // todo summary com transação vinculada no lookback, ligado ou não.
    when(implantationDateProvider.get()).thenReturn(LocalDate.of(2025, 1, 1));
    when(reconciliationSettingsService.getReconciliationLookbackMonths()).thenReturn(1);
    when(salesSummaryRepository.findStatsForAcquirerSaleSummaryReconciliation(any(), any(), any(), any()))
      .thenReturn(List.of());

    when(reconciliationSettingsService.isReprocessAcquirerSaleSummary()).thenReturn(false);
    service.reconcilePending(FinancialReconciliationTriggerType.MANUAL);

    when(reconciliationSettingsService.isReprocessAcquirerSaleSummary()).thenReturn(true);
    service.reconcilePending(FinancialReconciliationTriggerType.MANUAL);

    // Mesma consulta, com os mesmos parâmetros, independente do valor da flag.
    verify(salesSummaryRepository, times(2))
      .findStatsForAcquirerSaleSummaryReconciliation(any(), any(), any(), any());
  }

  @Test
  void doesNotMarkSummariesWithoutTransactionsBecauseTheTransactionStepAlreadyDoesIt() {
    when(implantationDateProvider.get()).thenReturn(LocalDate.of(2025, 1, 1));
    when(reconciliationSettingsService.getReconciliationLookbackMonths()).thenReturn(1);
    when(salesSummaryRepository.findStatsForAcquirerSaleSummaryReconciliation(any(), any(), any(), any()))
      .thenReturn(List.of());

    AcquirerSaleSummaryReconciliationResult result = service.reconcilePending(FinancialReconciliationTriggerType.MANUAL);

    assertThat(result.getSummariesWithoutTransactions()).isZero();
  }
}
