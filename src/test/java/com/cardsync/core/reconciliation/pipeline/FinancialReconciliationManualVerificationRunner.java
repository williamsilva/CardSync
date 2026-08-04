package com.cardsync.core.reconciliation.pipeline;

import com.cardsync.domain.model.enums.FinancialReconciliationTriggerType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Ferramenta manual de verificação contra o banco de dev real — não roda no {@code mvn test}
 * padrão (nome não bate com o include pattern do Surefire). Disparar explicitamente com:
 * {@code mvn -o -Dtest=FinancialReconciliationManualVerificationRunner#run test}
 */
@SpringBootTest
class FinancialReconciliationManualVerificationRunner {

  @Autowired
  private FinancialReconciliationPipelineService financialReconciliationPipelineService;

  @Test
  void run() {
    financialReconciliationPipelineService.run(FinancialReconciliationTriggerType.MANUAL);
  }
}
