package com.cardsync.core.reconciliation.summary;

import com.cardsync.core.config.ImplantationDateProvider;
import com.cardsync.domain.model.AcquirerEntity;
import com.cardsync.domain.model.CreditOrderEntity;
import com.cardsync.domain.model.SalesSummaryEntity;
import com.cardsync.domain.model.enums.StatusReconciliationEnum;
import com.cardsync.domain.repository.CreditOrderRepository;
import com.cardsync.domain.repository.SalesSummaryRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Cobre CreditOrderPreImplantationLinkingService: descoberto ao investigar por que ~6.100
 * ordens de crédito nunca chegam a ser elegíveis pra conciliação bancária — são órfãs (sem
 * SalesSummary) com rvDate anterior à implantação, excluídas por desenho do backfill padrão
 * (CreditOrderOrphanLinkingService), que só processa rvDate >= implantação.
 */
class CreditOrderPreImplantationLinkingServiceTest {

  private static final LocalDate IMPLANTATION_DATE = LocalDate.of(2024, 7, 1);

  private final ImplantationDateProvider implantationDateProvider = mock(ImplantationDateProvider.class);
  private final CreditOrderRepository creditOrderRepository = mock(CreditOrderRepository.class);
  private final SalesSummaryRepository salesSummaryRepository = mock(SalesSummaryRepository.class);

  private final CreditOrderPreImplantationLinkingService service = new CreditOrderPreImplantationLinkingService(
    implantationDateProvider, creditOrderRepository, salesSummaryRepository
  );

  private AcquirerEntity acquirer(UUID id) {
    AcquirerEntity a = new AcquirerEntity();
    a.setId(id);
    return a;
  }

  private CreditOrderEntity orphan(UUID id, AcquirerEntity acquirer, Integer pvCentralizer, Integer rvNumber) {
    CreditOrderEntity co = new CreditOrderEntity();
    co.setId(id);
    co.setAcquirer(acquirer);
    co.setPvCentralizer(pvCentralizer);
    co.setRvNumber(rvNumber);
    co.setRvDate(LocalDate.of(2023, 10, 1));
    co.setReleaseDate(LocalDate.of(2023, 11, 1));
    co.setReleaseValue(new BigDecimal("150.00"));
    return co;
  }

  private SalesSummaryEntity summary(UUID id, AcquirerEntity acquirer, Integer pvNumber, Integer rvNumber, LocalDate rvDate) {
    SalesSummaryEntity ss = new SalesSummaryEntity();
    ss.setId(id);
    ss.setAcquirer(acquirer);
    ss.setPvNumber(pvNumber);
    ss.setRvNumber(rvNumber);
    ss.setRvDate(rvDate);
    return ss;
  }

  @Test
  void previewLinksExactMatchAndNeverPersists() {
    UUID acquirerId = UUID.randomUUID();
    AcquirerEntity acq = acquirer(acquirerId);
    CreditOrderEntity co = orphan(UUID.randomUUID(), acq, 12345, 999);
    SalesSummaryEntity ss = summary(UUID.randomUUID(), acq, 12345, 999, LocalDate.of(2023, 10, 5));

    when(implantationDateProvider.get()).thenReturn(IMPLANTATION_DATE);
    when(creditOrderRepository.findOrphanedIdsBeforeImplantation(IMPLANTATION_DATE)).thenReturn(List.of(co.getId()));
    when(creditOrderRepository.findOrphanedByIdsWithCompany(List.of(co.getId()))).thenReturn(List.of(co));
    when(salesSummaryRepository.findByAcquirerIdInAndRvNumberIn(Set.of(acquirerId), Set.of(999))).thenReturn(List.of(ss));

    CreditOrderPreImplantationLinkingPreviewResult result = service.preview();

    assertThat(result.analyzed()).isEqualTo(1);
    assertThat(result.exactMatch()).isEqualTo(1);
    assertThat(result.pvMismatch()).isZero();
    assertThat(result.noMatch()).isZero();
    assertThat(result.candidates().getFirst().matchedSalesSummaryId()).isEqualTo(ss.getId());

    org.mockito.Mockito.verify(creditOrderRepository, org.mockito.Mockito.never()).saveAll(any());
  }

  @Test
  void applyLinksOnlyExactMatchAndSkipsPvMismatch() {
    UUID acquirerId = UUID.randomUUID();
    AcquirerEntity acq = acquirer(acquirerId);

    CreditOrderEntity exactOrder = orphan(UUID.randomUUID(), acq, 12345, 111);
    SalesSummaryEntity exactSummary = summary(UUID.randomUUID(), acq, 12345, 111, LocalDate.of(2023, 10, 5));

    // Mesmo RV, PV diferente entre a ordem (pvCentralizer=999) e o resumo (pvNumber=888).
    CreditOrderEntity mismatchOrder = orphan(UUID.randomUUID(), acq, 999, 222);
    SalesSummaryEntity mismatchSummary = summary(UUID.randomUUID(), acq, 888, 222, LocalDate.of(2023, 10, 6));

    when(implantationDateProvider.get()).thenReturn(IMPLANTATION_DATE);
    when(creditOrderRepository.findOrphanedIdsBeforeImplantation(IMPLANTATION_DATE))
      .thenReturn(List.of(exactOrder.getId(), mismatchOrder.getId()));
    when(creditOrderRepository.findOrphanedByIdsWithCompany(List.of(exactOrder.getId(), mismatchOrder.getId())))
      .thenReturn(List.of(exactOrder, mismatchOrder));
    when(salesSummaryRepository.findByAcquirerIdInAndRvNumberIn(Set.of(acquirerId), Set.of(111, 222)))
      .thenReturn(List.of(exactSummary, mismatchSummary));

    CreditOrderPreImplantationLinkingApplyResult result = service.apply(null);

    assertThat(result.analyzed()).isEqualTo(2);
    assertThat(result.linked()).isEqualTo(1);
    assertThat(result.pvMismatch()).isEqualTo(1);
    assertThat(result.noMatch()).isZero();
    assertThat(exactOrder.getSalesSummary()).isEqualTo(exactSummary);
    assertThat(mismatchOrder.getSalesSummary()).isNull();

    var saved = org.mockito.ArgumentCaptor.forClass(List.class);
    org.mockito.Mockito.verify(creditOrderRepository).saveAll(saved.capture());
    assertThat(saved.getValue()).containsExactly(exactOrder);
  }

  @Test
  void applyPropagatesReconciledStatusWhenMatchedSummaryAlreadyReconciled() {
    UUID acquirerId = UUID.randomUUID();
    AcquirerEntity acq = acquirer(acquirerId);
    CreditOrderEntity co = orphan(UUID.randomUUID(), acq, 12345, 999);
    SalesSummaryEntity ss = summary(UUID.randomUUID(), acq, 12345, 999, LocalDate.of(2023, 10, 5));
    ss.setCreditOrderStatus(StatusReconciliationEnum.RECONCILED);

    when(implantationDateProvider.get()).thenReturn(IMPLANTATION_DATE);
    when(creditOrderRepository.findOrphanedIdsBeforeImplantation(IMPLANTATION_DATE)).thenReturn(List.of(co.getId()));
    when(creditOrderRepository.findOrphanedByIdsWithCompany(List.of(co.getId()))).thenReturn(List.of(co));
    when(salesSummaryRepository.findByAcquirerIdInAndRvNumberIn(Set.of(acquirerId), Set.of(999))).thenReturn(List.of(ss));

    service.apply(null);

    assertThat(co.getSalesSummaryStatus()).isEqualTo(StatusReconciliationEnum.RECONCILED);
  }

  @Test
  void applyWithSelectedIdsRestrictsToThoseOrders() {
    UUID acquirerId = UUID.randomUUID();
    AcquirerEntity acq = acquirer(acquirerId);

    CreditOrderEntity order1 = orphan(UUID.randomUUID(), acq, 12345, 111);
    CreditOrderEntity order2 = orphan(UUID.randomUUID(), acq, 12345, 222);
    SalesSummaryEntity summary1 = summary(UUID.randomUUID(), acq, 12345, 111, LocalDate.of(2023, 10, 5));

    when(implantationDateProvider.get()).thenReturn(IMPLANTATION_DATE);
    when(creditOrderRepository.findOrphanedIdsBeforeImplantation(IMPLANTATION_DATE))
      .thenReturn(List.of(order1.getId(), order2.getId()));
    when(creditOrderRepository.findOrphanedByIdsWithCompany(List.of(order1.getId(), order2.getId())))
      .thenReturn(List.of(order1, order2));
    when(salesSummaryRepository.findByAcquirerIdInAndRvNumberIn(Set.of(acquirerId), Set.of(111)))
      .thenReturn(List.of(summary1));

    CreditOrderPreImplantationLinkingApplyResult result = service.apply(List.of(order1.getId()));

    assertThat(result.analyzed()).isEqualTo(1);
    assertThat(result.linked()).isEqualTo(1);
    assertThat(order1.getSalesSummary()).isEqualTo(summary1);
    assertThat(order2.getSalesSummary()).isNull();
  }

  @Test
  void previewReportsNoMatchWhenNoSalesSummaryExistsForAcquirerAndRv() {
    UUID acquirerId = UUID.randomUUID();
    AcquirerEntity acq = acquirer(acquirerId);
    CreditOrderEntity co = orphan(UUID.randomUUID(), acq, 12345, 999);

    when(implantationDateProvider.get()).thenReturn(IMPLANTATION_DATE);
    when(creditOrderRepository.findOrphanedIdsBeforeImplantation(IMPLANTATION_DATE)).thenReturn(List.of(co.getId()));
    when(creditOrderRepository.findOrphanedByIdsWithCompany(List.of(co.getId()))).thenReturn(List.of(co));
    when(salesSummaryRepository.findByAcquirerIdInAndRvNumberIn(Set.of(acquirerId), Set.of(999))).thenReturn(List.of());

    CreditOrderPreImplantationLinkingPreviewResult result = service.preview();

    assertThat(result.noMatch()).isEqualTo(1);
    assertThat(result.exactMatch()).isZero();
    assertThat(result.pvMismatch()).isZero();
  }
}
