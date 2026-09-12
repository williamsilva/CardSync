package com.cardsync.core.conciliation.analysis;

import com.cardsync.domain.model.TransactionAcqEntity;
import com.cardsync.domain.model.TransactionErpEntity;
import com.cardsync.domain.model.enums.StatusReconciliationEnum;
import com.cardsync.domain.model.enums.StatusTransactionEnum;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cobre o achado de análise profunda (2026-09-12): reconciliação automática (Etapa 1, ERP x
 * Adquirente) com reconcileAlreadyReconciled=true (flag "reprocessar vendas já conciliadas")
 * podia reavaliar vendas MANUALLY_RECONCILED - sobrescrevendo o status pra
 * AUTOMATICALLY_RECONCILED (apagando o rastro de resolução manual) ou pior, desfazendo o
 * pareamento se um registro novo tornasse o match ambíguo/divergente. Ver
 * isManuallyReconciledErpStatusTransaction/isManuallyReconciledAcquirerStatusTransaction
 * (usados como guarda no loop de runErpAcquirerReconciliation) e o mapeamento explícito
 * toStatusTransaction (que substituiu StatusTransactionEnum.fromCode(status.getCode()) - os
 * códigos dos dois enums colidem em posições diferentes, ex.: DIVERGENT=4 ~ DELETED=4).
 */
class ConciliationAnalysisServiceManuallyReconciledGuardTest {

  private final ConciliationAnalysisService service = new ConciliationAnalysisService(
    null, null, null, null, null, null, null, null, null, null
  );

  @Test
  void recognizesManuallyReconciledErpStatus() {
    TransactionErpEntity erp = new TransactionErpEntity();
    erp.setStatusTransaction(StatusTransactionEnum.MANUALLY_RECONCILED);

    assertThat(service.isManuallyReconciledErpStatusTransaction(erp)).isTrue();
  }

  @Test
  void doesNotFlagOtherErpStatusesAsManuallyReconciled() {
    TransactionErpEntity erp = new TransactionErpEntity();
    erp.setStatusTransaction(StatusTransactionEnum.AUTOMATICALLY_RECONCILED);

    assertThat(service.isManuallyReconciledErpStatusTransaction(erp)).isFalse();
  }

  @Test
  void recognizesManuallyReconciledAcquirerStatus() {
    TransactionAcqEntity acq = new TransactionAcqEntity();
    acq.setStatusTransaction(StatusTransactionEnum.MANUALLY_RECONCILED);

    assertThat(service.isManuallyReconciledAcquirerStatusTransaction(acq)).isTrue();
  }

  @Test
  void mapsPendingAndReconciledExplicitly() {
    assertThat(service.toStatusTransaction(StatusReconciliationEnum.PENDING))
      .isEqualTo(StatusTransactionEnum.PENDING);
    assertThat(service.toStatusTransaction(StatusReconciliationEnum.RECONCILED))
      .isEqualTo(StatusTransactionEnum.AUTOMATICALLY_RECONCILED);
  }

  @Test
  void failsLoudlyInsteadOfSilentlyMappingDivergentToDeleted() {
    // StatusReconciliationEnum.DIVERGENT tem código 4, que colide com
    // StatusTransactionEnum.DELETED (também código 4) - sem o mapeamento explícito, isso
    // marcaria a transação como DELETED por engano caso alguém use DIVERGENT no futuro.
    assertThatThrownBy(() -> service.toStatusTransaction(StatusReconciliationEnum.DIVERGENT))
      .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void failsLoudlyForAnyOtherUnmappedStatus() {
    assertThatThrownBy(() -> service.toStatusTransaction(StatusReconciliationEnum.CANCELED))
      .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> service.toStatusTransaction(StatusReconciliationEnum.PARTIALLY_RECONCILED))
      .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> service.toStatusTransaction(StatusReconciliationEnum.NULL))
      .isInstanceOf(IllegalStateException.class);
  }
}
