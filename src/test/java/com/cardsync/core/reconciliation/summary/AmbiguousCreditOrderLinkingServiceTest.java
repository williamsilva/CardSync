package com.cardsync.core.reconciliation.summary;

import com.cardsync.domain.model.AcquirerEntity;
import com.cardsync.domain.model.CreditOrderEntity;
import com.cardsync.domain.model.SalesSummaryEntity;
import com.cardsync.domain.model.enums.StatusReconciliationEnum;
import com.cardsync.domain.repository.CreditOrderRepository;
import com.cardsync.domain.repository.SalesSummaryRepository;
import com.cardsync.domain.repository.TransactionAcqRepository;
import com.nimbussystems.commons.legacy.exceptionhandler.BusinessException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cobre o resíduo que CreditOrderOrphanLinkingService deixa intencionalmente órfão: 2+
 * SalesSummary do mesmo lote (acquirer+pvNumber+rvNumber) com o MESMO liquidValue, onde
 * desambiguar por valor nunca aponta um único candidato. Aqui um operador escolhe manualmente
 * o vínculo — a classe só aplica a escolha, nunca decide sozinha.
 */
class AmbiguousCreditOrderLinkingServiceTest {

  private final CreditOrderRepository creditOrderRepository = mock(CreditOrderRepository.class);
  private final SalesSummaryRepository salesSummaryRepository = mock(SalesSummaryRepository.class);
  private final TransactionAcqRepository transactionAcqRepository = mock(TransactionAcqRepository.class);

  private final AmbiguousCreditOrderLinkingService service = new AmbiguousCreditOrderLinkingService(
    creditOrderRepository, salesSummaryRepository, transactionAcqRepository
  );

  @Test
  void listsBatchWithSummariesAndOrphanOrdersForEachAmbiguousKey() {
    AcquirerEntity acquirer = acquirer();
    SalesSummaryEntity summaryA = summary(acquirer, 1051583117, 556767019, "78.90");
    SalesSummaryEntity summaryB = summary(acquirer, 1051583117, 556767019, "78.90");
    CreditOrderEntity orderA = orphanOrder(acquirer, 1051583117, 556767019, "78.90");
    CreditOrderEntity orderB = orphanOrder(acquirer, 1051583117, 556767019, "78.90");

    when(salesSummaryRepository.findAmbiguousBatchKeys())
      .thenReturn(List.<Object[]>of(new Object[]{acquirer.getId(), 1051583117, 556767019, 2L}));
    when(creditOrderRepository.findOrphanedForSummary(acquirer.getId(), 1051583117, 556767019))
      .thenReturn(List.of(orderA, orderB));
    when(salesSummaryRepository.findByAcquirer_IdAndPvNumberAndRvNumber(acquirer.getId(), 1051583117, 556767019))
      .thenReturn(List.of(summaryA, summaryB));
    when(transactionAcqRepository.findMaxInstallmentBySalesSummaryIdIn(anyList())).thenReturn(List.of());
    when(creditOrderRepository.findInstallmentNumbersBySalesSummaryIdIn(anyList())).thenReturn(List.of());

    List<AmbiguousCreditOrderBatch> batches = service.listAmbiguousBatches();

    assertThat(batches).hasSize(1);
    AmbiguousCreditOrderBatch batch = batches.getFirst();
    assertThat(batch.acquirerId()).isEqualTo(acquirer.getId());
    assertThat(batch.pvNumber()).isEqualTo(1051583117);
    assertThat(batch.rvNumber()).isEqualTo(556767019);
    assertThat(batch.summaries()).hasSize(2);
    assertThat(batch.orders()).hasSize(2);
  }

  @Test
  void skipsKeyWhenNoOrphanRemainsAnymore() {
    AcquirerEntity acquirer = acquirer();

    when(salesSummaryRepository.findAmbiguousBatchKeys())
      .thenReturn(List.<Object[]>of(new Object[]{acquirer.getId(), 1051583117, 556767019, 2L}));
    when(creditOrderRepository.findOrphanedForSummary(acquirer.getId(), 1051583117, 556767019))
      .thenReturn(List.of());

    List<AmbiguousCreditOrderBatch> batches = service.listAmbiguousBatches();

    assertThat(batches).isEmpty();
  }

  @Test
  void linksOrderToChosenSummaryWhenSameKeyAndInstallmentFree() {
    AcquirerEntity acquirer = acquirer();
    SalesSummaryEntity summary = summary(acquirer, 1051583117, 556767019, "78.90");
    CreditOrderEntity order = orphanOrder(acquirer, 1051583117, 556767019, "78.90");

    when(creditOrderRepository.findById(order.getId())).thenReturn(java.util.Optional.of(order));
    when(salesSummaryRepository.findById(summary.getId())).thenReturn(java.util.Optional.of(summary));

    service.linkManually(order.getId(), summary.getId());

    assertThat(order.getSalesSummary()).isSameAs(summary);
    verify(creditOrderRepository).save(order);
  }

  @Test
  void propagatesReconciledStatusWhenSummaryAlreadyReconciled() {
    AcquirerEntity acquirer = acquirer();
    SalesSummaryEntity summary = summary(acquirer, 1051583117, 556767019, "78.90");
    summary.setCreditOrderStatus(StatusReconciliationEnum.RECONCILED);
    CreditOrderEntity order = orphanOrder(acquirer, 1051583117, 556767019, "78.90");

    when(creditOrderRepository.findById(order.getId())).thenReturn(java.util.Optional.of(order));
    when(salesSummaryRepository.findById(summary.getId())).thenReturn(java.util.Optional.of(summary));

    service.linkManually(order.getId(), summary.getId());

    assertThat(order.getSalesSummaryStatus()).isEqualTo(StatusReconciliationEnum.RECONCILED);
  }

  @Test
  void rejectsWhenOrderAlreadyLinked() {
    AcquirerEntity acquirer = acquirer();
    SalesSummaryEntity alreadyLinked = summary(acquirer, 1051583117, 556767019, "78.90");
    SalesSummaryEntity chosen = summary(acquirer, 1051583117, 556767019, "78.90");
    CreditOrderEntity order = orphanOrder(acquirer, 1051583117, 556767019, "78.90");
    order.setSalesSummary(alreadyLinked);

    when(creditOrderRepository.findById(order.getId())).thenReturn(java.util.Optional.of(order));

    assertThatThrownBy(() -> service.linkManually(order.getId(), chosen.getId()))
      .isInstanceOf(BusinessException.class);
    verify(creditOrderRepository, never()).save(any());
  }

  @Test
  void rejectsWhenOrderAndSummaryDoNotShareTheSameKey() {
    AcquirerEntity acquirer = acquirer();
    SalesSummaryEntity summary = summary(acquirer, 1051583117, 556767019, "78.90");
    CreditOrderEntity order = orphanOrder(acquirer, 1051583117, 999999999, "78.90");

    when(creditOrderRepository.findById(order.getId())).thenReturn(java.util.Optional.of(order));
    when(salesSummaryRepository.findById(summary.getId())).thenReturn(java.util.Optional.of(summary));

    assertThatThrownBy(() -> service.linkManually(order.getId(), summary.getId()))
      .isInstanceOf(BusinessException.class);
    verify(creditOrderRepository, never()).save(any());
  }

  @Test
  void rejectsWhenSummaryAlreadyHasCreditOrderForTheSameInstallment() {
    AcquirerEntity acquirer = acquirer();
    SalesSummaryEntity summary = summary(acquirer, 1051583117, 556767019, "78.90");
    CreditOrderEntity order = orphanOrder(acquirer, 1051583117, 556767019, "78.90");
    order.setInstallmentNumber(1);

    when(creditOrderRepository.findById(order.getId())).thenReturn(java.util.Optional.of(order));
    when(salesSummaryRepository.findById(summary.getId())).thenReturn(java.util.Optional.of(summary));
    when(creditOrderRepository.existsBySalesSummary_IdAndInstallmentNumber(summary.getId(), 1)).thenReturn(true);

    assertThatThrownBy(() -> service.linkManually(order.getId(), summary.getId()))
      .isInstanceOf(BusinessException.class);
    verify(creditOrderRepository, never()).save(any());
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
