package com.cardsync.core.reconciliation;

import com.cardsync.domain.model.CreditOrderEntity;
import com.cardsync.domain.model.ReleasesBankEntity;
import com.cardsync.domain.model.SalesSummaryEntity;
import com.cardsync.domain.model.enums.StatusPaymentBankEnum;
import com.cardsync.domain.model.enums.StatusReconciliationEnum;
import com.cardsync.domain.repository.CreditOrderRepository;
import com.cardsync.domain.repository.InstallmentAcqRepository;
import com.cardsync.domain.repository.ReleasesBankRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cobre a correção do bug de contador desatualizado: com reprocessBankAcquirer=true, uma
 * CreditOrderEntity já vinculada a um lançamento pode ser realocada para outro na mesma
 * execução (ver isOrderStillEligible). Sem recomputeReleasesAfterOrderReassignment, o
 * lançamento antigo nunca era revisitado e ficava com numberCreditOrders/reconciliationStatus
 * desatualizados (podendo continuar PAID sem nenhuma ordem real vinculada).
 */
class BankReconciliationServiceReassignmentTest {

  private final CreditOrderRepository creditOrderRepository = mock(CreditOrderRepository.class);
  private final ReleasesBankRepository releasesBankRepository = mock(ReleasesBankRepository.class);
  private final InstallmentAcqRepository installmentAcqRepository = mock(InstallmentAcqRepository.class);

  private final BankReconciliationService service = new BankReconciliationService(
    null, // entityManager — não usado por applyCreditOrderMatch
    null, // matcher — não usado por applyCreditOrderMatch
    null, // properties — não usado por applyCreditOrderMatch
    creditOrderRepository,
    releasesBankRepository,
    installmentAcqRepository,
    null, // transactionErpRepository — só usado se houver parcelas vinculadas
    null, // transactionAcqRepository — não usado por applyCreditOrderMatch
    null  // reconciliationSettingsService — não usado por applyCreditOrderMatch
  );

  @Test
  void reassigningOrderRecomputesPreviousReleaseFromRealRemainingCountAndRevertsToPendingWhenEmpty() {
    UUID previousReleaseId = UUID.randomUUID();
    ReleasesBankEntity previousRelease = new ReleasesBankEntity();
    previousRelease.setId(previousReleaseId);
    previousRelease.setNumberCreditOrders(3);
    previousRelease.setNumberReconciliations(3);
    previousRelease.setReconciliationStatus(StatusPaymentBankEnum.PAID);

    ReleasesBankEntity newRelease = new ReleasesBankEntity();
    newRelease.setId(UUID.randomUUID());
    newRelease.setReleaseValue(new BigDecimal("100.00"));

    CreditOrderEntity order = new CreditOrderEntity();
    order.setId(UUID.randomUUID());
    order.setReleaseValue(new BigDecimal("100.00"));
    order.setReleaseBank(previousRelease); // já vinculada a OUTRO lançamento antes deste match

    when(releasesBankRepository.findAllById(any())).thenReturn(List.of(previousRelease));
    when(creditOrderRepository.findByReleaseBank_Id(previousReleaseId)).thenReturn(List.of());
    when(installmentAcqRepository.findByReleaseBank_Id(previousReleaseId)).thenReturn(List.of());

    BankReconciliationMatcher.MatchResult matchResult =
      BankReconciliationMatcher.MatchResult.matched(List.of(order), new BigDecimal("100.00"), false);
    BankReconciliationResult.Counter counter =
      BankReconciliationResult.counter(BankReconciliationTriggerType.MANUAL, BankReconciliationMode.CREDIT_ORDER_ONLY);

    Map<UUID, Integer> reassignedCountByPreviousReleaseId = new LinkedHashMap<>();
    Set<UUID> affectedSalesSummaryIds = new HashSet<>();
    Set<UUID> affectedSalesSummaryIdsFromTransactions = new HashSet<>();
    service.applyCreditOrderMatch(
      newRelease, List.of(order), matchResult, counter, true,
      reassignedCountByPreviousReleaseId, affectedSalesSummaryIds, affectedSalesSummaryIdsFromTransactions
    );
    // O recomputo não roda mais dentro de applyCreditOrderMatch (ver comentário no método) —
    // o chamador real (reconcileEligibleCreditOrders) acumula por todo o laço e recomputa uma
    // única vez no final, exercitado aqui explicitamente.
    service.recomputeReleasesAfterOrderReassignment(reassignedCountByPreviousReleaseId);

    assertThat(order.getReleaseBank()).isSameAs(newRelease);
    assertThat(previousRelease.getNumberCreditOrders()).isZero();
    // numberReconciliations é decrementado pelas ordens efetivamente realocadas nesta chamada
    // (1), não zerado — mesma semântica de undoReconciliation, que também só decrementa.
    assertThat(previousRelease.getNumberReconciliations()).isEqualTo(2);
    assertThat(previousRelease.getReconciliationStatus()).isEqualTo(StatusPaymentBankEnum.PENDING);
    verify(releasesBankRepository).save(previousRelease);
  }

  @Test
  void reassigningOrderKeepsPreviousReleasePaidWhenOtherOrdersStillRemain() {
    UUID previousReleaseId = UUID.randomUUID();
    ReleasesBankEntity previousRelease = new ReleasesBankEntity();
    previousRelease.setId(previousReleaseId);
    previousRelease.setNumberCreditOrders(3);
    previousRelease.setNumberReconciliations(3);
    previousRelease.setReconciliationStatus(StatusPaymentBankEnum.PAID);

    ReleasesBankEntity newRelease = new ReleasesBankEntity();
    newRelease.setId(UUID.randomUUID());
    newRelease.setReleaseValue(new BigDecimal("50.00"));

    CreditOrderEntity order = new CreditOrderEntity();
    order.setId(UUID.randomUUID());
    order.setReleaseValue(new BigDecimal("50.00"));
    order.setReleaseBank(previousRelease);

    CreditOrderEntity stillLinkedOrder = new CreditOrderEntity();
    stillLinkedOrder.setId(UUID.randomUUID());
    stillLinkedOrder.setReleaseBank(previousRelease);

    when(releasesBankRepository.findAllById(any())).thenReturn(List.of(previousRelease));
    when(creditOrderRepository.findByReleaseBank_Id(previousReleaseId)).thenReturn(List.of(stillLinkedOrder));
    when(installmentAcqRepository.findByReleaseBank_Id(previousReleaseId)).thenReturn(List.of());

    BankReconciliationMatcher.MatchResult matchResult =
      BankReconciliationMatcher.MatchResult.matched(List.of(order), new BigDecimal("50.00"), false);
    BankReconciliationResult.Counter counter =
      BankReconciliationResult.counter(BankReconciliationTriggerType.MANUAL, BankReconciliationMode.CREDIT_ORDER_ONLY);

    Map<UUID, Integer> reassignedCountByPreviousReleaseId = new LinkedHashMap<>();
    Set<UUID> affectedSalesSummaryIds = new HashSet<>();
    Set<UUID> affectedSalesSummaryIdsFromTransactions = new HashSet<>();
    service.applyCreditOrderMatch(
      newRelease, List.of(order), matchResult, counter, true,
      reassignedCountByPreviousReleaseId, affectedSalesSummaryIds, affectedSalesSummaryIdsFromTransactions
    );
    service.recomputeReleasesAfterOrderReassignment(reassignedCountByPreviousReleaseId);

    // Ainda tem 1 ordem real vinculada — não deve reverter para PENDING, só corrigir o contador.
    assertThat(previousRelease.getNumberCreditOrders()).isEqualTo(1);
    assertThat(previousRelease.getNumberReconciliations()).isEqualTo(2);
    assertThat(previousRelease.getReconciliationStatus()).isEqualTo(StatusPaymentBankEnum.PAID);
  }

  @Test
  void matchingOrdersAlreadyOnTheSameReleaseDoesNotTriggerRecompute() {
    ReleasesBankEntity release = new ReleasesBankEntity();
    release.setId(UUID.randomUUID());
    release.setReleaseValue(new BigDecimal("100.00"));

    CreditOrderEntity order = new CreditOrderEntity();
    order.setId(UUID.randomUUID());
    order.setReleaseValue(new BigDecimal("100.00"));
    order.setReleaseBank(release); // já estava vinculada a este MESMO lançamento

    BankReconciliationMatcher.MatchResult matchResult =
      BankReconciliationMatcher.MatchResult.matched(List.of(order), new BigDecimal("100.00"), false);
    BankReconciliationResult.Counter counter =
      BankReconciliationResult.counter(BankReconciliationTriggerType.MANUAL, BankReconciliationMode.CREDIT_ORDER_ONLY);

    Map<UUID, Integer> reassignedCountByPreviousReleaseId = new LinkedHashMap<>();
    Set<UUID> affectedSalesSummaryIds = new HashSet<>();
    Set<UUID> affectedSalesSummaryIdsFromTransactions = new HashSet<>();
    service.applyCreditOrderMatch(
      release, List.of(order), matchResult, counter, true,
      reassignedCountByPreviousReleaseId, affectedSalesSummaryIds, affectedSalesSummaryIdsFromTransactions
    );

    // Ordem já estava no MESMO lançamento — não deve entrar no acumulado de realocação, então
    // o chamador (reconcileEligibleCreditOrders) nem chegaria a chamar o recomputo.
    assertThat(reassignedCountByPreviousReleaseId).isEmpty();
    verify(releasesBankRepository, org.mockito.Mockito.never()).findAllById(any());
  }

  /**
   * Cobre a correção do maior custo de performance encontrado em produção: antes,
   * applyCreditOrderMatch chamava updateSalesSummaryFromCreditOrder (1 query por ORDEM casada,
   * não só por realocação) dentro do laço — em lotes com centenas de matches isso disparava
   * centenas de auto-flushes do Hibernate por lote. Agora só coleta o id do resumo; quem
   * recomputa (1 única query em lote) é o chamador, no final do laço inteiro.
   */
  @Test
  void applyCreditOrderMatchOnlyCollectsSummaryIdWithoutQueryingPerOrder() {
    SalesSummaryEntity summary = new SalesSummaryEntity();
    UUID summaryId = UUID.randomUUID();
    summary.setId(summaryId);

    ReleasesBankEntity release = new ReleasesBankEntity();
    release.setId(UUID.randomUUID());
    release.setReleaseValue(new BigDecimal("100.00"));

    CreditOrderEntity order = new CreditOrderEntity();
    order.setId(UUID.randomUUID());
    order.setReleaseValue(new BigDecimal("100.00"));
    order.setSalesSummary(summary);

    BankReconciliationMatcher.MatchResult matchResult =
      BankReconciliationMatcher.MatchResult.matched(List.of(order), new BigDecimal("100.00"), false);
    BankReconciliationResult.Counter counter =
      BankReconciliationResult.counter(BankReconciliationTriggerType.MANUAL, BankReconciliationMode.CREDIT_ORDER_ONLY);

    Map<UUID, Integer> reassignedCountByPreviousReleaseId = new LinkedHashMap<>();
    Set<UUID> affectedSalesSummaryIds = new HashSet<>();
    Set<UUID> affectedSalesSummaryIdsFromTransactions = new HashSet<>();
    service.applyCreditOrderMatch(
      release, List.of(order), matchResult, counter, true,
      reassignedCountByPreviousReleaseId, affectedSalesSummaryIds, affectedSalesSummaryIdsFromTransactions
    );

    assertThat(affectedSalesSummaryIds).containsExactly(summaryId);
    verify(creditOrderRepository, org.mockito.Mockito.never()).findBySalesSummary_Id(any());
  }

  @Test
  void recomputeSalesSummariesFromCreditOrderIdsAggregatesInASingleQueryAcrossMultipleSummaries() {
    SalesSummaryEntity partiallyPaidSummary = new SalesSummaryEntity();
    UUID partiallyPaidSummaryId = UUID.randomUUID();
    partiallyPaidSummary.setId(partiallyPaidSummaryId);

    SalesSummaryEntity fullyPaidSummary = new SalesSummaryEntity();
    UUID fullyPaidSummaryId = UUID.randomUUID();
    fullyPaidSummary.setId(fullyPaidSummaryId);

    CreditOrderEntity paidOrder = new CreditOrderEntity();
    paidOrder.setId(UUID.randomUUID());
    paidOrder.setSalesSummary(partiallyPaidSummary);
    paidOrder.setStatusPaymentBank(StatusPaymentBankEnum.PAID);

    CreditOrderEntity pendingOrder = new CreditOrderEntity();
    pendingOrder.setId(UUID.randomUUID());
    pendingOrder.setSalesSummary(partiallyPaidSummary);
    pendingOrder.setStatusPaymentBank(StatusPaymentBankEnum.PENDING);

    CreditOrderEntity onlyOrder = new CreditOrderEntity();
    onlyOrder.setId(UUID.randomUUID());
    onlyOrder.setSalesSummary(fullyPaidSummary);
    onlyOrder.setStatusPaymentBank(StatusPaymentBankEnum.PAID);

    when(creditOrderRepository.findBySalesSummary_IdIn(Set.of(partiallyPaidSummaryId, fullyPaidSummaryId)))
      .thenReturn(List.of(paidOrder, pendingOrder, onlyOrder));

    service.recomputeSalesSummariesFromCreditOrderIds(Set.of(partiallyPaidSummaryId, fullyPaidSummaryId));

    assertThat(partiallyPaidSummary.getCreditOrderStatus()).isEqualTo(StatusReconciliationEnum.PARTIALLY_RECONCILED);
    assertThat(partiallyPaidSummary.getStatusPaymentBank()).isEqualTo(StatusPaymentBankEnum.PARTIALLY_PAID);
    assertThat(fullyPaidSummary.getCreditOrderStatus()).isEqualTo(StatusReconciliationEnum.RECONCILED);
    assertThat(fullyPaidSummary.getStatusPaymentBank()).isEqualTo(StatusPaymentBankEnum.PAID);
  }
}
