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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Achado real (auditoria 2026-09-13): cada ajuste de cancelamento era avaliado isoladamente
 * contra o valor total da venda - duas cancelações parciais da mesma venda (ex.: 50% cada, em
 * ajustes/arquivos diferentes) nunca fechavam 100% juntas. Estes testes cobrem o fallback de
 * agregação: quando o ajuste atual sozinho não cobre a venda, soma cancellationValueRequested
 * de todos os ajustes já vinculados à mesma transação antes de decidir.
 */
class AcquirerSaleCancellationServicePartialCancellationTest {

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
  void cancelsSaleWhenTwoPartialAdjustmentsTogetherCoverTheFullValue() {
    TransactionAcqEntity acq = withId(new TransactionAcqEntity());
    acq.setGrossValue(new BigDecimal("100.00"));
    acq.setStatusTransaction(StatusTransactionEnum.AUTOMATICALLY_RECONCILED);

    // Este ajuste sozinho só cobre metade da venda.
    AdjustmentEntity secondPartialAdjustment = withId(new AdjustmentEntity());
    secondPartialAdjustment.setTransaction(acq);
    secondPartialAdjustment.setCancellationValueRequested(new BigDecimal("50.00"));

    when(reconciliationSettingsService.isReprocessAcquirerSaleCancellations()).thenReturn(false);
    when(reconciliationSettingsService.getValueTolerance()).thenReturn(new BigDecimal("0.05"));
    when(adjustmentRepository.findIdsForAcquirerSaleCancellationReconciliation(anyBoolean(), anyInt()))
      .thenReturn(List.of(secondPartialAdjustment.getId()));
    when(adjustmentRepository.findBatchForAcquirerSaleCancellationReconciliation(anyCollection()))
      .thenReturn(List.of(secondPartialAdjustment));
    when(transactionErpRepository.findByTransactionAcqIdsForCancellationReconciliation(anyCollection()))
      .thenReturn(List.of());
    // Um primeiro ajuste de 50% já foi gravado antes (em outro arquivo/execução) - o agregado
    // para esta transação soma os dois e fecha o valor total da venda.
    when(adjustmentRepository.sumCancellationValueRequestedByTransactionId(acq.getId()))
      .thenReturn(new BigDecimal("100.00"));

    AcquirerSaleCancellationResult result = service.reconcilePending(FinancialReconciliationTriggerType.MANUAL);

    assertThat(acq.getStatusTransaction()).isEqualTo(StatusTransactionEnum.CANCELED);
    assertThat(result.getAcquirerSalesCanceled()).isEqualTo(1);
  }

  @Test
  void keepsSalePendingWhenAggregatedPartialsStillDoNotCoverTheFullValue() {
    TransactionAcqEntity acq = withId(new TransactionAcqEntity());
    acq.setGrossValue(new BigDecimal("100.00"));
    acq.setStatusTransaction(StatusTransactionEnum.AUTOMATICALLY_RECONCILED);

    AdjustmentEntity partialAdjustment = withId(new AdjustmentEntity());
    partialAdjustment.setTransaction(acq);
    partialAdjustment.setCancellationValueRequested(new BigDecimal("30.00"));

    when(reconciliationSettingsService.isReprocessAcquirerSaleCancellations()).thenReturn(false);
    when(reconciliationSettingsService.getValueTolerance()).thenReturn(new BigDecimal("0.05"));
    when(adjustmentRepository.findIdsForAcquirerSaleCancellationReconciliation(anyBoolean(), anyInt()))
      .thenReturn(List.of(partialAdjustment.getId()));
    when(adjustmentRepository.findBatchForAcquirerSaleCancellationReconciliation(anyCollection()))
      .thenReturn(List.of(partialAdjustment));
    when(transactionErpRepository.findByTransactionAcqIdsForCancellationReconciliation(anyCollection()))
      .thenReturn(List.of());
    // Mesmo somando este ajuste com outros já registrados, ainda falta cobrir a venda.
    when(adjustmentRepository.sumCancellationValueRequestedByTransactionId(acq.getId()))
      .thenReturn(new BigDecimal("60.00"));

    AcquirerSaleCancellationResult result = service.reconcilePending(FinancialReconciliationTriggerType.MANUAL);

    assertThat(acq.getStatusTransaction()).isEqualTo(StatusTransactionEnum.AUTOMATICALLY_RECONCILED);
    assertThat(result.getAcquirerSalesCanceled()).isZero();
    assertThat(result.getSkippedPartialCancellations()).isEqualTo(1);
  }

  private <T extends AuditableEntityBase> T withId(T entity) {
    entity.setId(UUID.randomUUID());
    return entity;
  }
}
