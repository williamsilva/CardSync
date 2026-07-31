package com.cardsync.core.reconciliation;

import com.cardsync.domain.model.CreditOrderEntity;
import com.cardsync.domain.model.SalesSummaryEntity;
import com.cardsync.domain.model.enums.StatusPaymentBankEnum;
import com.cardsync.domain.model.enums.StatusReconciliationEnum;
import com.cardsync.domain.repository.CreditOrderRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Cobre a correção do agregador de statusPaymentBank do resumo a partir das CreditOrder ligadas a
 * ele: "todas pagas" agora exige que o número de parcelas PAGAS bata com o total de parcelas
 * ESPERADO (installmentTotal), não só com o número de CreditOrder que já existem. Reproduz o caso
 * real encontrado (RV 8549241, Acquamania Multiplo Lazer): installmentTotal=3, só as parcelas 1 e
 * 3 existem (criadas fora de ordem, mesmo padrão documentado em
 * SaleSummarySpecs#gapAfterExistingDueSpec para o RV 56649219) — antes da correção, o resumo virava
 * "Pago" porque as duas que existiam estavam pagas, escondendo a parcela 2 ainda faltando ser
 * gerada.
 */
class BankReconciliationServiceCreditOrderPaymentAggregationTest {

  private final CreditOrderRepository creditOrderRepository = mock(CreditOrderRepository.class);

  private final BankReconciliationService service = new BankReconciliationService(
    null, null, null, creditOrderRepository, null, null, null, null, null, null
  );

  private CreditOrderEntity order(Integer installmentNumber, Integer installmentTotal, StatusPaymentBankEnum status) {
    CreditOrderEntity order = new CreditOrderEntity();
    order.setId(UUID.randomUUID());
    order.setInstallmentNumber(installmentNumber);
    order.setInstallmentTotal(installmentTotal);
    order.setStatusPaymentBank(status);
    return order;
  }

  @Test
  void missingInstallmentKeepsSummaryPartiallyPaidEvenWhenAllExistingOrdersArePaid() {
    CreditOrderEntity installment1 = order(1, 3, StatusPaymentBankEnum.PAID);
    CreditOrderEntity installment3 = order(3, 3, StatusPaymentBankEnum.PAID);
    // Parcela 2 nunca foi gerada — não existe CreditOrder pra ela.

    BankReconciliationService.PaymentAggregate aggregate =
      BankReconciliationService.aggregateCreditOrderPayment(List.of(installment1, installment3));

    assertThat(aggregate.allPaid()).isFalse();
    assertThat(aggregate.anyPaid()).isTrue();
  }

  @Test
  void allExpectedInstallmentsPresentAndPaidMarksAllPaid() {
    CreditOrderEntity installment1 = order(1, 3, StatusPaymentBankEnum.PAID);
    CreditOrderEntity installment2 = order(2, 3, StatusPaymentBankEnum.PAID);
    CreditOrderEntity installment3 = order(3, 3, StatusPaymentBankEnum.PAID);

    BankReconciliationService.PaymentAggregate aggregate =
      BankReconciliationService.aggregateCreditOrderPayment(List.of(installment1, installment2, installment3));

    assertThat(aggregate.allPaid()).isTrue();
    assertThat(aggregate.anyPaid()).isTrue();
  }

  @Test
  void noExistingOrderPaidMarksNeitherAllNorAnyPaid() {
    CreditOrderEntity installment1 = order(1, 2, StatusPaymentBankEnum.PENDING);

    BankReconciliationService.PaymentAggregate aggregate =
      BankReconciliationService.aggregateCreditOrderPayment(List.of(installment1));

    assertThat(aggregate.allPaid()).isFalse();
    assertThat(aggregate.anyPaid()).isFalse();
  }

  @Test
  void emptySiblingsIsNeitherAllNorAnyPaid() {
    BankReconciliationService.PaymentAggregate aggregate =
      BankReconciliationService.aggregateCreditOrderPayment(List.of());

    assertThat(aggregate.allPaid()).isFalse();
    assertThat(aggregate.anyPaid()).isFalse();
  }

  /**
   * Ponta a ponta via recomputeSalesSummariesFromCreditOrderIds (o caminho batch usado pelo laço
   * de conciliação bancária): reproduz o RV 8549241 real — antes da correção, summary.statusPaymentBank
   * virava PAID aqui; agora fica PARTIALLY_PAID, refletindo a parcela 2 ainda faltando.
   */
  @Test
  void recomputeFromCreditOrderIdsKeepsSummaryPartiallyPaidWithMissingInstallment() {
    UUID summaryId = UUID.randomUUID();
    SalesSummaryEntity summary = new SalesSummaryEntity();
    summary.setId(summaryId);

    CreditOrderEntity installment1 = order(1, 3, StatusPaymentBankEnum.PAID);
    installment1.setSalesSummary(summary);
    CreditOrderEntity installment3 = order(3, 3, StatusPaymentBankEnum.PAID);
    installment3.setSalesSummary(summary);

    when(creditOrderRepository.findBySalesSummary_IdIn(Set.of(summaryId)))
      .thenReturn(List.of(installment1, installment3));

    service.recomputeSalesSummariesFromCreditOrderIds(Set.of(summaryId));

    assertThat(summary.getStatusPaymentBank()).isEqualTo(StatusPaymentBankEnum.PARTIALLY_PAID);
    assertThat(summary.getCreditOrderStatus()).isEqualTo(StatusReconciliationEnum.PARTIALLY_RECONCILED);
  }
}
