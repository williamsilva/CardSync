package com.cardsync.core.reconciliation.cancellation;

import com.cardsync.core.conciliation.ReconciliationSettingsService;
import com.cardsync.core.reconciliation.summary.SalesSummaryTransactionReconciliationService;
import com.cardsync.domain.model.AdjustmentEntity;
import com.cardsync.domain.model.AuditableEntityBase;
import com.cardsync.domain.model.TransactionAcqEntity;
import com.cardsync.domain.model.enums.FinancialReconciliationTriggerType;
import com.cardsync.domain.model.enums.StatusTransactionEnum;
import com.cardsync.domain.repository.AdjustmentRepository;
import com.cardsync.domain.repository.TransactionAcqRepository;
import com.cardsync.domain.repository.TransactionErpRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Cobre o achado de análise profunda da Etapa 3 (2026-09-12): diferente da Etapa 1, um
 * cancelamento informado pela adquirente NÃO deve ser bloqueado mesmo quando a venda já estava
 * MANUALLY_RECONCILED (é uma informação nova e autoritativa - a venda foi de fato cancelada no
 * mundo real) - mas antes disso não havia NENHUMA visibilidade de que isso tinha acontecido.
 * Este teste garante que (a) o cancelamento continua acontecendo normalmente e (b) fica visível
 * via manuallyReconciledOverridden no resultado.
 */
class AcquirerSaleCancellationServiceManuallyReconciledTest {

  private final EntityManager entityManager = mock(EntityManager.class);
  private final ReconciliationSettingsService reconciliationSettingsService = mock(ReconciliationSettingsService.class);
  private final SalesSummaryTransactionReconciliationService salesSummaryTransactionReconciliationService =
    mock(SalesSummaryTransactionReconciliationService.class);
  private final AdjustmentRepository adjustmentRepository = mock(AdjustmentRepository.class);
  private final TransactionAcqRepository transactionAcqRepository = mock(TransactionAcqRepository.class);
  private final TransactionErpRepository transactionErpRepository = mock(TransactionErpRepository.class);

  private final AcquirerSaleCancellationService service = new AcquirerSaleCancellationService(
    entityManager,
    reconciliationSettingsService,
    salesSummaryTransactionReconciliationService,
    adjustmentRepository,
    transactionAcqRepository,
    transactionErpRepository
  );

  @Test
  void cancelsManuallyReconciledSaleButFlagsItAsOverridden() {
    TransactionAcqEntity acq = withId(new TransactionAcqEntity());
    acq.setStatusTransaction(StatusTransactionEnum.MANUALLY_RECONCILED);
    acq.setGrossValue(new BigDecimal("100.00"));

    AdjustmentEntity adjustment = withId(new AdjustmentEntity());
    adjustment.setTransaction(acq);
    adjustment.setCancellationValueRequested(new BigDecimal("100.00"));

    when(reconciliationSettingsService.isReprocessAcquirerSaleCancellations()).thenReturn(false);
    when(reconciliationSettingsService.getValueTolerance()).thenReturn(new BigDecimal("0.05"));
    when(adjustmentRepository.findIdsForAcquirerSaleCancellationReconciliation(anyBoolean(), anyInt()))
      .thenReturn(List.of(adjustment.getId()));
    when(adjustmentRepository.findBatchForAcquirerSaleCancellationReconciliation(anyCollection()))
      .thenReturn(List.of(adjustment));
    when(transactionErpRepository.findByTransactionAcqIdsForCancellationReconciliation(anyCollection()))
      .thenReturn(List.of());

    AcquirerSaleCancellationResult result = service.reconcilePending(FinancialReconciliationTriggerType.MANUAL);

    // O cancelamento NÃO é bloqueado - a informação da adquirente prevalece.
    assertThat(acq.getStatusTransaction()).isEqualTo(StatusTransactionEnum.CANCELED);
    assertThat(result.getAcquirerSalesCanceled()).isEqualTo(1);
    // Mas fica visível que isso sobrescreveu uma resolução manual.
    assertThat(result.getManuallyReconciledOverridden()).isEqualTo(1);
  }

  private <T extends AuditableEntityBase> T withId(T entity) {
    entity.setId(UUID.randomUUID());
    return entity;
  }
}
