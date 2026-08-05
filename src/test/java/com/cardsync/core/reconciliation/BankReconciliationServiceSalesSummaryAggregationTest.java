package com.cardsync.core.reconciliation;

import com.cardsync.domain.model.InstallmentAcqEntity;
import com.cardsync.domain.model.SalesSummaryEntity;
import com.cardsync.domain.model.TransactionAcqEntity;
import com.cardsync.domain.model.enums.StatusPaymentBankEnum;
import com.cardsync.domain.model.enums.StatusReconciliationEnum;
import com.cardsync.domain.repository.TransactionAcqRepository;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cobre a correção de updateSalesSummaryFromTransaction (bug "Divergente" no resumo) e, na
 * sequência, a correção de performance sobre ela: o resumo parcelado tem uma TransactionAcqEntity
 * por parcela, e é normal que algumas já tenham batido no banco e outras não (os lançamentos
 * bancários chegam parcela a parcela) — copiar o status de UMA transação pro resumo inteiro
 * fazia o resumo ficar "Divergente" indevidamente. A correção passou a agregar TODAS as
 * transações do resumo — mas fazendo isso com 1 query por transação (updateStatusTransactionBatched
 * chamando updateSalesSummaryFromTransaction inline) essa query rodava centenas de vezes por
 * lote em produção, cada uma forçando auto-flush do Hibernate. Agora updateStatusTransactionBatched
 * só coleta o id do resumo; quem agrega (1 única query em lote, ver recomputeSalesSummariesFromTransactionIds)
 * é o chamador, no final do laço inteiro.
 *
 * Também cobre o achado real da Cielo: recomputeSalesSummariesFromTransactionIds passou a setar
 * creditOrderStatus junto com statusPaymentBank — antes só o método irmão (baseado em
 * CreditOrderEntity vinculada) fazia isso, e esse vínculo falha sistematicamente pra Cielo (Chave
 * UR não é única por venda), deixando "Ordem Crédito" preso em PENDING mesmo com o pagamento já
 * confirmado (tela de Resumo de Vendas mostrando as 2 colunas incoerentes entre si).
 */
class BankReconciliationServiceSalesSummaryAggregationTest {

  private final TransactionAcqRepository transactionAcqRepository = mock(TransactionAcqRepository.class);

  private final BankReconciliationService service = new BankReconciliationService(
    null, null, null, null, null, null, null, transactionAcqRepository, null
  );

  @Test
  void updateStatusTransactionBatchedOnlyCollectsSummaryIdWithoutQueryingPerTransaction() {
    UUID summaryId = UUID.randomUUID();
    SalesSummaryEntity summary = new SalesSummaryEntity();
    summary.setId(summaryId);

    TransactionAcqEntity transaction = new TransactionAcqEntity();
    transaction.setId(UUID.randomUUID());
    transaction.setSalesSummary(summary);

    InstallmentAcqEntity liquidated = new InstallmentAcqEntity();
    liquidated.setStatusPaymentBank(BankReconciliationStatus.RECONCILED.getCode());

    Set<UUID> affectedSalesSummaryIdsFromTransactions = new HashSet<>();
    service.updateStatusTransactionBatched(transaction, List.of(liquidated), null, affectedSalesSummaryIdsFromTransactions);

    assertThat(transaction.getStatusPaymentBank()).isEqualTo(StatusPaymentBankEnum.PAID);
    assertThat(affectedSalesSummaryIdsFromTransactions).containsExactly(summaryId);
    verify(transactionAcqRepository, never()).findBySalesSummary_Id(any());
  }

  @Test
  void doesNotOverwriteSummaryWithJustTheLastTransactionWhenSiblingIsStillPending() {
    UUID summaryId = UUID.randomUUID();
    SalesSummaryEntity summary = new SalesSummaryEntity();
    summary.setId(summaryId);

    TransactionAcqEntity paidTransaction = new TransactionAcqEntity();
    paidTransaction.setId(UUID.randomUUID());
    paidTransaction.setSalesSummary(summary);

    TransactionAcqEntity pendingSibling = new TransactionAcqEntity();
    pendingSibling.setId(UUID.randomUUID());
    pendingSibling.setSalesSummary(summary);
    pendingSibling.setStatusPaymentBank(StatusPaymentBankEnum.PENDING);

    when(transactionAcqRepository.findBySalesSummary_IdIn(Set.of(summaryId)))
      .thenReturn(List.of(paidTransaction, pendingSibling));

    InstallmentAcqEntity liquidatedInstallment = new InstallmentAcqEntity();
    liquidatedInstallment.setStatusPaymentBank(BankReconciliationStatus.RECONCILED.getCode());

    Set<UUID> affectedSalesSummaryIdsFromTransactions = new HashSet<>();
    service.updateStatusTransactionBatched(paidTransaction, List.of(liquidatedInstallment), null, affectedSalesSummaryIdsFromTransactions);
    service.recomputeSalesSummariesFromTransactionIds(affectedSalesSummaryIdsFromTransactions);

    assertThat(paidTransaction.getStatusPaymentBank()).isEqualTo(StatusPaymentBankEnum.PAID);
    // Antes da correção, isto seria PAID (copiado direto de paidTransaction) — errado, pois
    // pendingSibling ainda não bateu no banco.
    assertThat(summary.getStatusPaymentBank()).isEqualTo(StatusPaymentBankEnum.PARTIALLY_PAID);
    assertThat(summary.getCreditOrderStatus()).isEqualTo(StatusReconciliationEnum.PARTIALLY_RECONCILED);
  }

  @Test
  void singleDivergentTransactionMarksSummaryAsPartiallyPaidInsteadOfDivergent() {
    UUID summaryId = UUID.randomUUID();
    SalesSummaryEntity summary = new SalesSummaryEntity();
    summary.setId(summaryId);

    TransactionAcqEntity transaction = new TransactionAcqEntity();
    transaction.setId(UUID.randomUUID());
    transaction.setSalesSummary(summary);

    when(transactionAcqRepository.findBySalesSummary_IdIn(Set.of(summaryId)))
      .thenReturn(List.of(transaction));

    InstallmentAcqEntity liquidated = new InstallmentAcqEntity();
    liquidated.setStatusPaymentBank(BankReconciliationStatus.RECONCILED.getCode());
    InstallmentAcqEntity stillPending = new InstallmentAcqEntity();
    stillPending.setStatusPaymentBank(BankReconciliationStatus.PENDING.getCode());

    Set<UUID> affectedSalesSummaryIdsFromTransactions = new HashSet<>();
    service.updateStatusTransactionBatched(transaction, List.of(liquidated, stillPending), null, affectedSalesSummaryIdsFromTransactions);
    service.recomputeSalesSummariesFromTransactionIds(affectedSalesSummaryIdsFromTransactions);

    assertThat(transaction.getStatusPaymentBank()).isEqualTo(StatusPaymentBankEnum.DIVERGENT);
    // "Divergente" no nível da transação é normal (parcelamento ainda sendo pago aos poucos),
    // não um erro — o resumo deve refletir isso como pagamento parcial, não como divergência.
    assertThat(summary.getStatusPaymentBank()).isEqualTo(StatusPaymentBankEnum.PARTIALLY_PAID);
    assertThat(summary.getCreditOrderStatus()).isEqualTo(StatusReconciliationEnum.PARTIALLY_RECONCILED);
  }

  @Test
  void allSiblingTransactionsPaidMarksSummaryAsPaid() {
    UUID summaryId = UUID.randomUUID();
    SalesSummaryEntity summary = new SalesSummaryEntity();
    summary.setId(summaryId);

    TransactionAcqEntity transaction = new TransactionAcqEntity();
    transaction.setId(UUID.randomUUID());
    transaction.setSalesSummary(summary);

    TransactionAcqEntity paidSibling = new TransactionAcqEntity();
    paidSibling.setId(UUID.randomUUID());
    paidSibling.setSalesSummary(summary);
    paidSibling.setStatusPaymentBank(StatusPaymentBankEnum.PAID);

    when(transactionAcqRepository.findBySalesSummary_IdIn(Set.of(summaryId)))
      .thenReturn(List.of(transaction, paidSibling));

    InstallmentAcqEntity liquidated = new InstallmentAcqEntity();
    liquidated.setStatusPaymentBank(BankReconciliationStatus.RECONCILED.getCode());

    Set<UUID> affectedSalesSummaryIdsFromTransactions = new HashSet<>();
    service.updateStatusTransactionBatched(transaction, List.of(liquidated), null, affectedSalesSummaryIdsFromTransactions);
    service.recomputeSalesSummariesFromTransactionIds(affectedSalesSummaryIdsFromTransactions);

    assertThat(summary.getStatusPaymentBank()).isEqualTo(StatusPaymentBankEnum.PAID);
    // Achado real Cielo: creditOrderStatus precisa acompanhar statusPaymentBank aqui, já que o
    // vínculo CreditOrder↔SalesSummary (que faria o método irmão setar isso) falha pra Cielo.
    assertThat(summary.getCreditOrderStatus()).isEqualTo(StatusReconciliationEnum.RECONCILED);
  }

  @Test
  void noSiblingPaidResetsCreditOrderStatusBackToPending() {
    UUID summaryId = UUID.randomUUID();
    SalesSummaryEntity summary = new SalesSummaryEntity();
    summary.setId(summaryId);
    summary.setCreditOrderStatus(StatusReconciliationEnum.RECONCILED);
    summary.setStatusPaymentBank(StatusPaymentBankEnum.PAID);

    TransactionAcqEntity transaction = new TransactionAcqEntity();
    transaction.setId(UUID.randomUUID());
    transaction.setSalesSummary(summary);
    transaction.setStatusPaymentBank(StatusPaymentBankEnum.PENDING);

    when(transactionAcqRepository.findBySalesSummary_IdIn(Set.of(summaryId)))
      .thenReturn(List.of(transaction));

    service.recomputeSalesSummariesFromTransactionIds(Set.of(summaryId));

    assertThat(summary.getStatusPaymentBank()).isEqualTo(StatusPaymentBankEnum.PENDING);
    assertThat(summary.getCreditOrderStatus()).isEqualTo(StatusReconciliationEnum.PENDING);
  }
}
