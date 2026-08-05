package com.cardsync.core.reconciliation;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Ferramenta manual de reparo pontual — não roda no {@code mvn test} padrão (nome não bate com o
 * include pattern do Surefire). Disparar explicitamente com:
 * {@code mvn -o -Dtest=RecomputeSalesSummaryCreditOrderStatusRunner#run test}
 *
 * Corrige, uma única vez, o histórico já processado antes de
 * BankReconciliationService#recomputeSalesSummariesFromTransactionIds passar a setar também
 * creditOrderStatus (achado real Cielo: "Ordem Crédito" preso em PENDING mesmo com
 * statusPaymentBank já PAID, ver feedback_cielo_chave_ur_not_unique).
 */
@SpringBootTest
class RecomputeSalesSummaryCreditOrderStatusRunner {

  @Autowired
  private BankReconciliationService bankReconciliationService;

  @Test
  void run() {
    int total = bankReconciliationService.recomputeAllSalesSummariesFromTransactions();
    System.out.println("SalesSummary recomputados: " + total);
  }
}
