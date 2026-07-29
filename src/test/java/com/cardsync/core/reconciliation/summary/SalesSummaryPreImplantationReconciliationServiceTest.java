package com.cardsync.core.reconciliation.summary;

import com.cardsync.core.conciliation.ReconciliationSettingsService;
import com.cardsync.core.config.ImplantationDateProvider;
import com.cardsync.domain.model.SalesSummaryEntity;
import com.cardsync.domain.model.enums.FinancialReconciliationTriggerType;
import com.cardsync.domain.repository.SalesSummaryRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cobre SalesSummaryPreImplantationReconciliationService: descoberto ao investigar por que
 * milhares de CreditOrder pós go-live nunca ficam elegíveis pra conciliação bancária (Etapa 6) —
 * o SalesSummary da venda original é anterior à implantação (rvDate < implantationDate) e por
 * isso nunca é reavaliado por SalesSummaryCreditOrderReconciliationService#reconcilePending, nem
 * no fluxo normal nem no backfill de lookback já existente (ambas as consultas exigem rvDate >=
 * implantationDate) — mesmo quando a parcela (CreditOrder) só vence bem depois do go-live.
 */
class SalesSummaryPreImplantationReconciliationServiceTest {

  private static final LocalDate IMPLANTATION_DATE = LocalDate.of(2024, 7, 1);

  private final ImplantationDateProvider implantationDateProvider = mock(ImplantationDateProvider.class);
  private final ReconciliationSettingsService reconciliationSettingsService = mock(ReconciliationSettingsService.class);
  private final SalesSummaryRepository salesSummaryRepository = mock(SalesSummaryRepository.class);

  // Instância real (sem repositórios) só para reaproveitar a lógica pura de
  // shouldGenerateSyntheticCreditOrder, igual ao teste existente de
  // SalesSummaryCreditOrderReconciliationServiceTest.
  private final SalesSummaryCreditOrderReconciliationService realReconciliationService =
    new SalesSummaryCreditOrderReconciliationService(null, null, null, null, null);

  private final SalesSummaryPreImplantationReconciliationService service = new SalesSummaryPreImplantationReconciliationService(
    implantationDateProvider, reconciliationSettingsService, salesSummaryRepository, realReconciliationService
  );

  private SalesSummaryCreditOrderStats stats(UUID id, long creditOrdersCount, BigDecimal grossValue, int installmentTotal) {
    return new SalesSummaryCreditOrderStats(id, creditOrdersCount, grossValue, installmentTotal);
  }

  @Test
  void previewClassifiesFullyReconciledPartialAndWithoutOrders() {
    UUID fullyReconciledId = UUID.randomUUID();
    UUID partialId = UUID.randomUUID();
    UUID withoutOrdersId = UUID.randomUUID();

    when(implantationDateProvider.get()).thenReturn(IMPLANTATION_DATE);
    when(reconciliationSettingsService.isReprocessSalesSummaryCreditOrder()).thenReturn(false);
    when(salesSummaryRepository.findStatsForSalesSummaryCreditOrderReconciliationPreImplantation(
      eq(false), any(), any(), eq(IMPLANTATION_DATE)
    )).thenReturn(List.of(
      stats(fullyReconciledId, 2, new BigDecimal("100.00"), 2),
      stats(partialId, 1, new BigDecimal("50.00"), 2),
      stats(withoutOrdersId, 0, new BigDecimal("30.00"), 1)
    ));

    SalesSummaryEntity withoutOrdersSummary = new SalesSummaryEntity();
    withoutOrdersSummary.setId(withoutOrdersId);
    withoutOrdersSummary.setModality(1); // CASH_DEBIT — elegível pra geração sintética
    when(salesSummaryRepository.findBatchForSalesSummaryCreditOrderReconciliation(List.of(withoutOrdersId)))
      .thenReturn(List.of(withoutOrdersSummary));

    SalesSummaryPreImplantationPreviewResult result = service.preview();

    assertThat(result.summariesAnalyzed()).isEqualTo(3);
    assertThat(result.wouldReconcile()).isEqualTo(1);
    assertThat(result.wouldPartiallyReconcile()).isEqualTo(1);
    assertThat(result.wouldGenerateSynthetic()).isEqualTo(1);
    assertThat(result.wouldRemainPending()).isZero();
    assertThat(result.totalGrossValueAnalyzed()).isEqualByComparingTo(new BigDecimal("180.00"));
  }

  @Test
  void previewSeparatesSyntheticEligibleFromGenuinelyMissing() {
    UUID eligibleId = UUID.randomUUID();
    UUID notEligibleId = UUID.randomUUID();

    when(implantationDateProvider.get()).thenReturn(IMPLANTATION_DATE);
    when(reconciliationSettingsService.isReprocessSalesSummaryCreditOrder()).thenReturn(false);
    when(salesSummaryRepository.findStatsForSalesSummaryCreditOrderReconciliationPreImplantation(
      eq(false), any(), any(), eq(IMPLANTATION_DATE)
    )).thenReturn(List.of(
      stats(eligibleId, 0, BigDecimal.ZERO, 1),
      stats(notEligibleId, 0, BigDecimal.ZERO, 1)
    ));

    SalesSummaryEntity eligible = new SalesSummaryEntity();
    eligible.setId(eligibleId);
    eligible.setModality(1); // CASH_DEBIT

    SalesSummaryEntity notEligible = new SalesSummaryEntity();
    notEligible.setId(notEligibleId);
    notEligible.setModality(2); // CASH_CREDIT parcelado normal — precisa de CreditOrder real, nunca sintética

    when(salesSummaryRepository.findBatchForSalesSummaryCreditOrderReconciliation(List.of(eligibleId, notEligibleId)))
      .thenReturn(List.of(eligible, notEligible));

    SalesSummaryPreImplantationPreviewResult result = service.preview();

    assertThat(result.wouldGenerateSynthetic()).isEqualTo(1);
    assertThat(result.wouldRemainPending()).isEqualTo(1);
  }

  @Test
  void previewNeverTriggersApply() {
    when(implantationDateProvider.get()).thenReturn(IMPLANTATION_DATE);
    when(reconciliationSettingsService.isReprocessSalesSummaryCreditOrder()).thenReturn(false);
    when(salesSummaryRepository.findStatsForSalesSummaryCreditOrderReconciliationPreImplantation(
      anyBoolean(), any(), any(), eq(IMPLANTATION_DATE)
    )).thenReturn(List.of());

    service.preview();

    verify(salesSummaryRepository, never()).updateCreditOrderStatusByIds(any(), any());
  }

  @Test
  void applyDelegatesToReconcilePreImplantationOnRealService() {
    SalesSummaryCreditOrderReconciliationService mockedReconciliationService =
      mock(SalesSummaryCreditOrderReconciliationService.class);
    SalesSummaryPreImplantationReconciliationService serviceWithMockedDelegate =
      new SalesSummaryPreImplantationReconciliationService(
        implantationDateProvider, reconciliationSettingsService, salesSummaryRepository, mockedReconciliationService
      );

    SalesSummaryCreditOrderReconciliationResult expected = SalesSummaryCreditOrderReconciliationResult.builder()
      .trigger(FinancialReconciliationTriggerType.MANUAL)
      .summariesAnalyzed(3)
      .summariesReconciled(2)
      .build();
    when(mockedReconciliationService.reconcilePreImplantation(FinancialReconciliationTriggerType.MANUAL))
      .thenReturn(expected);

    SalesSummaryCreditOrderReconciliationResult result = serviceWithMockedDelegate.apply();

    assertThat(result).isSameAs(expected);
  }
}
