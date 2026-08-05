package com.cardsync.core.reconciliation.summary;

import com.cardsync.core.config.ImplantationDateProvider;
import com.cardsync.core.conciliation.ReconciliationSettingsService;
import com.cardsync.domain.model.AcquirerEntity;
import com.cardsync.domain.model.CreditOrderEntity;
import com.cardsync.domain.model.SalesSummaryEntity;
import com.cardsync.domain.repository.CreditOrderRepository;
import com.cardsync.domain.repository.SalesSummaryRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

/**
 * Achado real (Cielo): acquirer+pvCentralizer+rvNumber pode achar VÁRIAS SalesSummary (rvNumber
 * é uma chave de lote de liquidação, não por venda) — antes, a mais recente "ganhava" todas as
 * CreditOrder órfãs do lote, coladas na venda errada. Agora desambigua por valor
 * (releaseValue↔liquidValue), só vinculando quando exatamente uma candidata bate.
 */
class CreditOrderOrphanLinkingServiceTest {

  private final ImplantationDateProvider implantationDateProvider = mock(ImplantationDateProvider.class);
  private final ReconciliationSettingsService reconciliationSettingsService = mock(ReconciliationSettingsService.class);
  private final CreditOrderRepository creditOrderRepository = mock(CreditOrderRepository.class);
  private final SalesSummaryRepository salesSummaryRepository = mock(SalesSummaryRepository.class);

  private final CreditOrderOrphanLinkingService service = new CreditOrderOrphanLinkingService(
    implantationDateProvider, reconciliationSettingsService, creditOrderRepository, salesSummaryRepository
  );

  @Test
  void linksToTheOnlyCandidateWhenKeyHasJustOneSummary() {
    AcquirerEntity acquirer = acquirer();
    SalesSummaryEntity summary = summary(acquirer, 1051583117, 361022950, "110.18");
    CreditOrderEntity order = orphanOrder(acquirer, 1051583117, 361022950, "110.18");

    stubImplantationAndLookback();
    when(creditOrderRepository.findOrphanedIdsWithinDateRange(any(), any())).thenReturn(List.of(order.getId()));
    when(creditOrderRepository.findOrphanedByIds(anyList())).thenReturn(List.of(order));
    when(salesSummaryRepository.findCandidatesForCreditOrderLinking(any(), any(), any())).thenReturn(List.of(summary));

    int linked = service.linkOrphanedCreditOrders();

    assertThat(linked).isEqualTo(1);
    assertThat(order.getSalesSummary()).isSameAs(summary);
  }

  @Test
  void disambiguatesByValueWhenMultipleSummariesShareTheSameBatchKey() {
    // Achado real: 7 SalesSummary diferentes compartilhando o mesmo rvNumber (lote) — aqui, 3
    // pra manter o teste enxuto. A ordem órfã só pode pertencer à que bate por valor.
    AcquirerEntity acquirer = acquirer();
    SalesSummaryEntity summaryA = summary(acquirer, 1051583117, 361022950, "68.19");
    SalesSummaryEntity summaryB = summary(acquirer, 1051583117, 361022950, "112.98");
    SalesSummaryEntity summaryC = summary(acquirer, 1051583117, 361022950, "83.57");
    CreditOrderEntity order = orphanOrder(acquirer, 1051583117, 361022950, "112.98");

    stubImplantationAndLookback();
    when(creditOrderRepository.findOrphanedIdsWithinDateRange(any(), any())).thenReturn(List.of(order.getId()));
    when(creditOrderRepository.findOrphanedByIds(anyList())).thenReturn(List.of(order));
    when(salesSummaryRepository.findCandidatesForCreditOrderLinking(any(), any(), any()))
      .thenReturn(List.of(summaryA, summaryB, summaryC));

    int linked = service.linkOrphanedCreditOrders();

    assertThat(linked).isEqualTo(1);
    assertThat(order.getSalesSummary()).isSameAs(summaryB);
    assertThat(summaryA.getCreditOrders()).isEmpty();
    assertThat(summaryC.getCreditOrders()).isEmpty();
  }

  @Test
  void leavesOrderOrphanedWhenNoCandidateInTheBatchMatchesByValue() {
    // Caso real: parcela isolada de uma venda parcelada — o valor da parcela não é o total do
    // resumo de nenhuma das vendas do lote. Ficar órfã é mais seguro que colar na errada.
    AcquirerEntity acquirer = acquirer();
    SalesSummaryEntity summaryA = summary(acquirer, 1051583117, 361022950, "68.19");
    SalesSummaryEntity summaryB = summary(acquirer, 1051583117, 361022950, "112.98");
    CreditOrderEntity order = orphanOrder(acquirer, 1051583117, 361022950, "25.00");

    stubImplantationAndLookback();
    when(creditOrderRepository.findOrphanedIdsWithinDateRange(any(), any())).thenReturn(List.of(order.getId()));
    when(creditOrderRepository.findOrphanedByIds(anyList())).thenReturn(List.of(order));
    when(salesSummaryRepository.findCandidatesForCreditOrderLinking(any(), any(), any()))
      .thenReturn(List.of(summaryA, summaryB));

    int linked = service.linkOrphanedCreditOrders();

    assertThat(linked).isZero();
    assertThat(order.getSalesSummary()).isNull();
  }

  @Test
  void leavesOrderOrphanedWhenValueMatchesMoreThanOneCandidate() {
    // 2 vendas do mesmo lote com o MESMO valor (coincidência real possível) — ambíguo demais
    // pra decidir, fica órfã em vez de arriscar a errada.
    AcquirerEntity acquirer = acquirer();
    SalesSummaryEntity summaryA = summary(acquirer, 1051583117, 361022950, "75.00");
    SalesSummaryEntity summaryB = summary(acquirer, 1051583117, 361022950, "75.00");
    CreditOrderEntity order = orphanOrder(acquirer, 1051583117, 361022950, "75.00");

    stubImplantationAndLookback();
    when(creditOrderRepository.findOrphanedIdsWithinDateRange(any(), any())).thenReturn(List.of(order.getId()));
    when(creditOrderRepository.findOrphanedByIds(anyList())).thenReturn(List.of(order));
    when(salesSummaryRepository.findCandidatesForCreditOrderLinking(any(), any(), any()))
      .thenReturn(List.of(summaryA, summaryB));

    int linked = service.linkOrphanedCreditOrders();

    assertThat(linked).isZero();
    assertThat(order.getSalesSummary()).isNull();
  }

  @Test
  void directLinkingForManualSummaryOnlyClaimsOrdersMatchingItsOwnValue() {
    AcquirerEntity acquirer = acquirer();
    SalesSummaryEntity summary = summary(acquirer, 1051583117, 361022950, "112.98");
    CreditOrderEntity matchingOrder = orphanOrder(acquirer, 1051583117, 361022950, "112.98");
    CreditOrderEntity foreignOrder = orphanOrder(acquirer, 1051583117, 361022950, "68.19");

    when(creditOrderRepository.findOrphanedForSummary(acquirer.getId(), 1051583117, 361022950))
      .thenReturn(List.of(matchingOrder, foreignOrder));

    int linked = service.linkOrphanedCreditOrdersForSummary(summary);

    assertThat(linked).isEqualTo(1);
    assertThat(matchingOrder.getSalesSummary()).isSameAs(summary);
    assertThat(foreignOrder.getSalesSummary()).isNull();
    verify(creditOrderRepository, never()).saveAll(List.of(foreignOrder));
  }

  private void stubImplantationAndLookback() {
    when(implantationDateProvider.get()).thenReturn(LocalDate.of(2025, 1, 1));
    when(reconciliationSettingsService.getReconciliationLookbackMonths()).thenReturn(6);
  }

  private AcquirerEntity acquirer() {
    AcquirerEntity acquirer = new AcquirerEntity();
    acquirer.setId(UUID.randomUUID());
    acquirer.setFantasyName("Cielo");
    return acquirer;
  }

  private SalesSummaryEntity summary(AcquirerEntity acquirer, int pvNumber, int rvNumber, String liquidValue) {
    SalesSummaryEntity summary = new SalesSummaryEntity();
    summary.setId(UUID.randomUUID());
    summary.setAcquirer(acquirer);
    summary.setPvNumber(pvNumber);
    summary.setRvNumber(rvNumber);
    summary.setLiquidValue(new BigDecimal(liquidValue));
    summary.setRvDate(LocalDate.of(2025, 1, 10));
    return summary;
  }

  private CreditOrderEntity orphanOrder(AcquirerEntity acquirer, int pvCentralizer, int rvNumber, String releaseValue) {
    CreditOrderEntity order = new CreditOrderEntity();
    order.setId(UUID.randomUUID());
    order.setAcquirer(acquirer);
    order.setPvCentralizer(pvCentralizer);
    order.setRvNumber(rvNumber);
    order.setReleaseValue(new BigDecimal(releaseValue));
    return order;
  }
}
