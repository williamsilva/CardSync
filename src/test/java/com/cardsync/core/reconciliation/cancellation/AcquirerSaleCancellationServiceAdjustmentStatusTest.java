package com.cardsync.core.reconciliation.cancellation;

import com.cardsync.core.conciliation.ReconciliationSettingsService;
import com.cardsync.core.reconciliation.summary.SalesSummaryTransactionReconciliationService;
import com.cardsync.domain.model.AdjustmentEntity;
import com.cardsync.domain.model.AuditableEntityBase;
import com.cardsync.domain.model.TransactionAcqEntity;
import com.cardsync.domain.model.enums.AdjustmentStatusEnum;
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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Achado real (auditoria 2026-09-13): AdjustmentStatusEnum.ADJUSTED existia mas nunca era
 * atribuído por nada - o ajuste que efetivamente cancelou uma venda ficava PENDING pra sempre.
 */
class AcquirerSaleCancellationServiceAdjustmentStatusTest {

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
  void marksAdjustmentAsAdjustedWhenItCancelsTheSale() {
    TransactionAcqEntity acq = withId(new TransactionAcqEntity());
    acq.setGrossValue(new BigDecimal("100.00"));
    acq.setStatusTransaction(StatusTransactionEnum.AUTOMATICALLY_RECONCILED);

    AdjustmentEntity adjustment = withId(new AdjustmentEntity());
    adjustment.setTransaction(acq);
    adjustment.setCancellationValueRequested(new BigDecimal("100.00"));
    adjustment.setAdjustmentStatus(AdjustmentStatusEnum.PENDING);

    stubBatch(adjustment);

    service.reconcilePending(FinancialReconciliationTriggerType.MANUAL);

    assertThat(acq.getStatusTransaction()).isEqualTo(StatusTransactionEnum.CANCELED);
    assertThat(adjustment.getAdjustmentStatus()).isEqualTo(AdjustmentStatusEnum.ADJUSTED);
  }

  @Test
  void neverOverwritesAManuallyDecidedAdjustmentStatus() {
    TransactionAcqEntity acq = withId(new TransactionAcqEntity());
    acq.setGrossValue(new BigDecimal("100.00"));
    acq.setStatusTransaction(StatusTransactionEnum.AUTOMATICALLY_RECONCILED);

    AdjustmentEntity adjustment = withId(new AdjustmentEntity());
    adjustment.setTransaction(acq);
    adjustment.setCancellationValueRequested(new BigDecimal("100.00"));
    adjustment.setAdjustmentStatus(AdjustmentStatusEnum.FAVORED_COMPANY);

    stubBatch(adjustment);

    service.reconcilePending(FinancialReconciliationTriggerType.MANUAL);

    // A venda cancela normalmente - a informação da adquirente prevalece sobre o pareamento -
    // mas a decisão humana já registrada sobre o AJUSTE em si não é mexida.
    assertThat(acq.getStatusTransaction()).isEqualTo(StatusTransactionEnum.CANCELED);
    assertThat(adjustment.getAdjustmentStatus()).isEqualTo(AdjustmentStatusEnum.FAVORED_COMPANY);
  }

  private void stubBatch(AdjustmentEntity adjustment) {
    when(reconciliationSettingsService.isReprocessAcquirerSaleCancellations()).thenReturn(false);
    when(reconciliationSettingsService.getValueTolerance()).thenReturn(new BigDecimal("0.05"));
    when(adjustmentRepository.findIdsForAcquirerSaleCancellationReconciliation(anyBoolean(), anyInt()))
      .thenReturn(List.of(adjustment.getId()));
    when(adjustmentRepository.findBatchForAcquirerSaleCancellationReconciliation(anyCollection()))
      .thenReturn(List.of(adjustment));
    when(transactionErpRepository.findByTransactionAcqIdsForCancellationReconciliation(anyCollection()))
      .thenReturn(List.of());
  }

  private <T extends AuditableEntityBase> T withId(T entity) {
    entity.setId(UUID.randomUUID());
    return entity;
  }
}
