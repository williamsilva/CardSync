package com.cardsync.core.reconciliation.cancellation;

import com.cardsync.core.conciliation.ReconciliationSettingsService;
import com.cardsync.domain.model.AdjustmentEntity;
import com.cardsync.domain.model.InstallmentAcqEntity;
import com.cardsync.domain.model.InstallmentErpEntity;
import com.cardsync.domain.model.TransactionAcqEntity;
import com.cardsync.domain.model.TransactionErpEntity;
import com.cardsync.domain.model.enums.*;
import com.cardsync.domain.repository.AdjustmentRepository;
import com.cardsync.domain.repository.TransactionAcqRepository;
import com.cardsync.domain.repository.TransactionErpRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class AcquirerSaleCancellationService {

  private static final int BATCH_SIZE = 1_000;

  private final EntityManager entityManager;
  private final ReconciliationSettingsService reconciliationSettingsService;
  private final AdjustmentRepository adjustmentRepository;
  private final TransactionAcqRepository transactionAcqRepository;
  private final TransactionErpRepository transactionErpRepository;

  /**
   * Cancela vendas quando a adquirente informa cancelamento total via ajuste financeiro.
   *
   * Regras principais:
   * - considera apenas ajustes já vinculados a uma TransactionAcq;
   * - só cancela quando o valor de cancelamento cobre o valor bruto/original da venda dentro da tolerância configurada;
   * - cancelamentos parciais continuam sendo tratados pelas etapas de ajustes/taxas, sem cancelar a venda;
   * - propaga o cancelamento para a venda ERP vinculada e suas parcelas;
   * - marca taxa e pagamento como finalizados/cancelados para não bloquear as etapas seguintes da esteira.
   */
  @Transactional
  public AcquirerSaleCancellationResult reconcilePending(FinancialReconciliationTriggerType trigger) {
    OffsetDateTime startedAt = OffsetDateTime.now();
    boolean reprocess = reconciliationSettingsService.isReprocessAcquirerSaleCancellations();
    BigDecimal tolerance = reconciliationSettingsService.getValueTolerance();

    List<UUID> adjustmentIds = adjustmentRepository.findIdsForAcquirerSaleCancellationReconciliation(
      reprocess,
      StatusTransactionEnum.CANCELED.getCode()
    );

    Counter counter = new Counter(trigger, startedAt);

    int totalBatches = (int) Math.ceil(adjustmentIds.size() / (double) BATCH_SIZE);

    log.info(
      "📌 Etapa de cancelamentos da adquirente iniciada. trigger={}, candidatos={}, batchSize={}, batches={}, reprocess={}, tolerance={}",
      trigger,
      adjustmentIds.size(),
      BATCH_SIZE,
      totalBatches,
      reprocess,
      tolerance
    );

    for (int start = 0, batch = 1; start < adjustmentIds.size(); start += BATCH_SIZE, batch++) {
      List<UUID> batchIds = adjustmentIds.subList(start, Math.min(start + BATCH_SIZE, adjustmentIds.size()));
      List<AdjustmentEntity> adjustments = adjustmentRepository.findBatchForAcquirerSaleCancellationReconciliation(batchIds);

      BatchResult batchResult = processBatch(adjustments, tolerance, reprocess);
      counter.merge(batchResult);

      entityManager.flush();
      entityManager.clear();

      log.info(
        "🔄 Etapa de cancelamentos da adquirente: batch={}/{}, ajustesAnalisados={}, cancelamentosTotais={}, " +
          "vendasAdqCanceladas={}, vendasErpCanceladas={}, parciaisIgnorados={}, jaCanceladasIgnoradas={}, totalAdqCanceladas={}",
        batch,
        totalBatches,
        batchResult.adjustmentsAnalyzed,
        batchResult.fullCancellationsIdentified,
        batchResult.acquirerSalesCanceled,
        batchResult.erpSalesCanceled,
        batchResult.skippedPartialCancellations,
        batchResult.skippedAlreadyCanceled,
        counter.acquirerSalesCanceled
      );
    }

    OffsetDateTime finishedAt = OffsetDateTime.now();
    AcquirerSaleCancellationResult result = counter.toResult(finishedAt);

    log.info(
      "✅ Etapa de cancelamentos da adquirente finalizada. trigger={}, ajustesAnalisados={}, cancelamentosTotais={}, " +
        "vendasAdqCanceladas={}, vendasErpCanceladas={}, parcelasAdqCanceladas={}, parcelasErpCanceladas={}, " +
        "parciaisIgnorados={}, semVenda={}, jaCanceladasIgnoradas={}, duraçãoTotal={}s",
      trigger,
      result.getAdjustmentsAnalyzed(),
      result.getFullCancellationsIdentified(),
      result.getAcquirerSalesCanceled(),
      result.getErpSalesCanceled(),
      result.getAcquirerInstallmentsCanceled(),
      result.getErpInstallmentsCanceled(),
      result.getSkippedPartialCancellations(),
      result.getSkippedWithoutTransaction(),
      result.getSkippedAlreadyCanceled(),
      Duration.between(startedAt, finishedAt).toSeconds()
    );

    return result;
  }

  private BatchResult processBatch(List<AdjustmentEntity> adjustments, BigDecimal tolerance, boolean reprocess) {
    BatchResult result = new BatchResult();

    if (adjustments == null || adjustments.isEmpty()) {
      return result;
    }

    Map<UUID, TransactionAcqEntity> acquirerSalesToSave = new LinkedHashMap<>();
    Map<UUID, TransactionErpEntity> erpSalesToSave = new LinkedHashMap<>();
    Map<UUID, TransactionErpEntity> erpByAcquirerId = findErpSalesByAcquirerId(adjustments);

    for (AdjustmentEntity adjustment : adjustments) {
      result.adjustmentsAnalyzed++;

      TransactionAcqEntity acq = adjustment.getTransaction();
      if (acq == null || acq.getId() == null) {
        result.skippedWithoutTransaction++;
        continue;
      }

      TransactionErpEntity erp = erpByAcquirerId.get(acq.getId());
      boolean acqAlreadyCanceled = isCanceled(acq.getStatusTransaction().getCode());
      boolean erpAlreadyCanceled = erp == null || isCanceled(StatusTransactionEnum.toCode(erp.getStatusTransaction()));

      if (!reprocess && acqAlreadyCanceled && erpAlreadyCanceled) {
        result.skippedAlreadyCanceled++;
        continue;
      }

      if (!isFullCancellation(adjustment, acq, tolerance)) {
        result.skippedPartialCancellations++;
        continue;
      }

      result.fullCancellationsIdentified++;

      LocalDate cancellationDate = resolveCancellationDate(adjustment, acq);
      StatusTransactionReasonEnum cancellationReason = resolveCancellationReason(adjustment);
      OffsetDateTime now = OffsetDateTime.now();

      if (cancelAcquirerSale(acq, adjustment, cancellationReason, cancellationDate, now, reprocess)) {
        result.acquirerSalesCanceled++;
        result.acquirerInstallmentsCanceled += cancelAcquirerInstallments(acq, cancellationDate);
        acquirerSalesToSave.put(acq.getId(), acq);
      }

      if (erp != null && cancelErpSale(erp, adjustment, cancellationReason, cancellationDate, now, reprocess)) {
        result.erpSalesCanceled++;
        result.erpInstallmentsCanceled += cancelErpInstallments(erp, cancellationDate);
        erpSalesToSave.put(erp.getId(), erp);
      }
    }

    if (!acquirerSalesToSave.isEmpty()) {
      transactionAcqRepository.saveAll(acquirerSalesToSave.values());
    }

    if (!erpSalesToSave.isEmpty()) {
      transactionErpRepository.saveAll(erpSalesToSave.values());
    }

    return result;
  }

  private Map<UUID, TransactionErpEntity> findErpSalesByAcquirerId(List<AdjustmentEntity> adjustments) {
    List<UUID> acquirerIds = adjustments.stream()
      .map(AdjustmentEntity::getTransaction)
      .filter(Objects::nonNull)
      .map(TransactionAcqEntity::getId)
      .filter(Objects::nonNull)
      .distinct()
      .toList();

    if (acquirerIds.isEmpty()) {
      return Map.of();
    }

    Map<UUID, TransactionErpEntity> result = new LinkedHashMap<>();
    List<TransactionErpEntity> erpSales = transactionErpRepository.findByTransactionAcqIdsForCancellationReconciliation(acquirerIds);

    for (TransactionErpEntity erp : erpSales) {
      if (erp.getTransactionAcq() != null && erp.getTransactionAcq().getId() != null) {
        result.put(erp.getTransactionAcq().getId(), erp);
      }
    }

    return result;
  }

  private boolean isFullCancellation(AdjustmentEntity adjustment, TransactionAcqEntity acq, BigDecimal tolerance) {
    BigDecimal canceledValue = abs(firstNonNull(adjustment.getCancellationValueRequested(), adjustment.getAdjustmentValue()));
    if (isZeroOrNull(canceledValue)) {
      return false;
    }

    BigDecimal baseValue = abs(firstNonNull(adjustment.getTransactionValue(), acq.getGrossValue(), acq.getPurchaseValue()));
    if (isZeroOrNull(baseValue)) {
      return hasCancellationDescription(adjustment);
    }

    BigDecimal newTransactionValue = adjustment.getNewTransactionValue();
    if (newTransactionValue != null && abs(newTransactionValue).compareTo(tolerance) <= 0) {
      return true;
    }

    return canceledValue.add(tolerance).compareTo(baseValue) >= 0;
  }

  private boolean cancelAcquirerSale(
    TransactionAcqEntity acq,
    AdjustmentEntity adjustment,
    StatusTransactionReasonEnum cancellationReason,
    LocalDate cancellationDate,
    OffsetDateTime reconciliationDate,
    boolean reprocess
  ) {
    if (!reprocess && isCanceled(acq.getStatusTransaction().getCode())) {
      return false;
    }

    boolean changed = false;
    changed |= setIfDifferent(acq::getStatusTransaction, acq::setStatusTransaction, StatusTransactionEnum.CANCELED);
    changed |= setIfDifferent(acq::getStatusTransactionReason, acq::setStatusTransactionReason, reasonCode(cancellationReason));
    changed |= setIfDifferent(acq::getStatusPaymentBank, acq::setStatusPaymentBank, StatusPaymentBankEnum.CANCELED);
    changed |= setIfDifferent(acq::getFeeReconciliationStatus, acq::setFeeReconciliationStatus, FeeReconciliationStatusEnum.RECONCILED);
    changed |= setIfDifferent(acq::getCanceledDate, acq::setCanceledDate, cancellationDate);
    changed |= setIfDifferent(acq::getSaleReconciliationDate, acq::setSaleReconciliationDate, reconciliationDate);

    if (acq.getAdjustment() == null) {
      acq.setAdjustment(adjustment);
      changed = true;
    }

    return changed;
  }

  private boolean cancelErpSale(
    TransactionErpEntity erp,
    AdjustmentEntity adjustment,
    StatusTransactionReasonEnum cancellationReason,
    LocalDate cancellationDate,
    OffsetDateTime reconciliationDate,
    boolean reprocess
  ) {
    if (!reprocess && isCanceled(StatusTransactionEnum.toCode(erp.getStatusTransaction()))) {
      return false;
    }

    boolean changed = false;
    changed |= setIfDifferent(erp::getStatusTransaction, erp::setStatusTransaction, StatusTransactionEnum.CANCELED);
    changed |= setIfDifferent(erp::getStatusTransactionReason, erp::setStatusTransactionReason, reasonCode(cancellationReason));
    changed |= setIfDifferent(erp::getFeeReconciliationStatus, erp::setFeeReconciliationStatus, FeeReconciliationStatusEnum.RECONCILED);
    changed |= setIfDifferent(erp::getCanceledDate, erp::setCanceledDate, cancellationDate);
    changed |= setIfDifferent(erp::getSaleReconciliationDate, erp::setSaleReconciliationDate, reconciliationDate);

    if (erp.getAdjustment() == null) {
      erp.setAdjustment(adjustment);
      changed = true;
    }

    return changed;
  }

  private int cancelAcquirerInstallments(TransactionAcqEntity acq, LocalDate cancellationDate) {
    if (acq.getInstallments() == null || acq.getInstallments().isEmpty()) {
      return 0;
    }

    int updated = 0;
    for (InstallmentAcqEntity installment : acq.getInstallments()) {
      boolean changed = false;
      changed |= setIfDifferent(installment::getInstallmentStatus, installment::setInstallmentStatus, StatusInstallmentEnum.CANCELED.getCode());
      changed |= setIfDifferent(installment::getStatusPaymentBank, installment::setStatusPaymentBank, StatusPaymentBankEnum.CANCELED.getCode());
      changed |= setIfDifferent(installment::getCancellationDate, installment::setCancellationDate, cancellationDate);
      if (changed) {
        updated++;
      }
    }
    return updated;
  }

  private int cancelErpInstallments(TransactionErpEntity erp, LocalDate cancellationDate) {
    if (erp.getInstallments() == null || erp.getInstallments().isEmpty()) {
      return 0;
    }

    int updated = 0;
    for (InstallmentErpEntity installment : erp.getInstallments()) {
      boolean changed = false;
      changed |= setIfDifferent(installment::getInstallmentStatus, installment::setInstallmentStatus, StatusInstallmentEnum.CANCELED.getCode());
      changed |= setIfDifferent(installment::getStatusPaymentBank, installment::setStatusPaymentBank, StatusPaymentBankEnum.CANCELED.getCode());
      changed |= setIfDifferent(installment::getCancellationDate, installment::setCancellationDate, cancellationDate);
      if (changed) {
        updated++;
      }
    }
    return updated;
  }

  private LocalDate resolveCancellationDate(AdjustmentEntity adjustment, TransactionAcqEntity acq) {
    return firstNonNull(
      adjustment.getAdjustmentDate(),
      adjustment.getCreditDate(),
      adjustment.getTransactionDate(),
      acq.getCanceledDate(),
      acq.getSaleDate() != null ? acq.getSaleDate().toLocalDate() : null
    );
  }

  /**
   * Resolve o motivo do cancelamento usando os campos informados pela adquirente.
   *
   * A ordem é intencionalmente conservadora: só grava um motivo específico quando
   * o texto/código do ajuste indica uma causa clara. Quando não for possível
   * identificar com segurança, mantém CANCEL_VENDAS como fallback.
   */
  private StatusTransactionReasonEnum resolveCancellationReason(AdjustmentEntity adjustment) {
    String text = normalizeText(
      nullToBlank(adjustment.getAdjustmentDescription()) + " " +
        nullToBlank(adjustment.getAdjustmentType()) + " " +
        nullToBlank(adjustment.getDebitType()) + " " +
        nullToBlank(adjustment.getRawAdjustmentCode()) + " " +
        nullToBlank(adjustment.getSourceRecordIdentifier())
    );

    if (containsAny(text, "chargeback", "contestacao", "contestac", "disputa", "dispute")) {
      return StatusTransactionReasonEnum.CHARGEBACK;
    }

    if (containsAny(text, "fraude", "fraud")) {
      return StatusTransactionReasonEnum.CANCELLATION_FRAUD;
    }

    if (containsAny(text, "duplicidade", "duplicado", "duplicate", "duplic")) {
      return StatusTransactionReasonEnum.CANCELLATION_DUPLICATE;
    }

    if (containsAny(text, "devolucao", "devolu", "refund", "reembolso")) {
      return StatusTransactionReasonEnum.CANCELLATION_RETURN;
    }

    if (containsAny(text, "estorno", "storno", "reversal", "revert")) {
      return StatusTransactionReasonEnum.CANCELLATION_REFUND;
    }

    if (containsAny(text, "erro", "error", "operacional", "captura", "processamento", "lancamento")) {
      return StatusTransactionReasonEnum.CANCELLATION_OPERATIONAL_ERROR;
    }

    if (containsAny(text, "cancelamento", "cancelado", "cancelada", "cancel")) {
      return StatusTransactionReasonEnum.CANCELLATION_ACQUIRER;
    }

    return StatusTransactionReasonEnum.CANCEL_VENDAS;
  }

  private StatusTransactionReasonEnum reasonCode(StatusTransactionReasonEnum reason) {
    return firstNonNull(reason, StatusTransactionReasonEnum.CANCEL_VENDAS);
  }

  private String normalizeText(String value) {
    if (value == null || value.isBlank()) {
      return "";
    }

    return java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD)
      .replaceAll("\\p{M}", "")
      .toLowerCase();
  }

  private boolean containsAny(String text, String... terms) {
    if (text == null || text.isBlank() || terms == null) {
      return false;
    }

    for (String term : terms) {
      if (term != null && !term.isBlank() && text.contains(term)) {
        return true;
      }
    }

    return false;
  }

  private boolean hasCancellationDescription(AdjustmentEntity adjustment) {
    String text = String.join(" ",
      nullToBlank(adjustment.getAdjustmentDescription()),
      nullToBlank(adjustment.getAdjustmentType()),
      nullToBlank(adjustment.getDebitType()),
      nullToBlank(adjustment.getRawAdjustmentCode())
    ).toLowerCase();

    return text.contains("cancel") || text.contains("estorno") || text.contains("devolu");
  }

  private boolean isCanceled(Integer status) {
    return Objects.equals(status, StatusTransactionEnum.CANCELED.getCode());
  }

  private boolean isZeroOrNull(BigDecimal value) {
    return value == null || BigDecimal.ZERO.compareTo(value) == 0;
  }

  private BigDecimal abs(BigDecimal value) {
    return value == null ? null : value.abs();
  }

  @SafeVarargs
  private final <T> T firstNonNull(T... values) {
    if (values == null) {
      return null;
    }
    for (T value : values) {
      if (value != null) {
        return value;
      }
    }
    return null;
  }

  private String nullToBlank(String value) {
    return value == null ? "" : value;
  }

  private <T> boolean setIfDifferent(Supplier<T> getter, Consumer<T> setter, T newValue) {
    if (newValue == null || Objects.equals(getter.get(), newValue)) {
      return false;
    }
    setter.accept(newValue);
    return true;
  }

  private static class Counter {
    private final FinancialReconciliationTriggerType trigger;
    private final OffsetDateTime startedAt;
    private int adjustmentsAnalyzed;
    private int fullCancellationsIdentified;
    private int acquirerSalesCanceled;
    private int erpSalesCanceled;
    private int acquirerInstallmentsCanceled;
    private int erpInstallmentsCanceled;
    private int skippedPartialCancellations;
    private int skippedWithoutTransaction;
    private int skippedAlreadyCanceled;

    private Counter(FinancialReconciliationTriggerType trigger, OffsetDateTime startedAt) {
      this.trigger = trigger;
      this.startedAt = startedAt;
    }

    private void merge(BatchResult batch) {
      adjustmentsAnalyzed += batch.adjustmentsAnalyzed;
      fullCancellationsIdentified += batch.fullCancellationsIdentified;
      acquirerSalesCanceled += batch.acquirerSalesCanceled;
      erpSalesCanceled += batch.erpSalesCanceled;
      acquirerInstallmentsCanceled += batch.acquirerInstallmentsCanceled;
      erpInstallmentsCanceled += batch.erpInstallmentsCanceled;
      skippedPartialCancellations += batch.skippedPartialCancellations;
      skippedWithoutTransaction += batch.skippedWithoutTransaction;
      skippedAlreadyCanceled += batch.skippedAlreadyCanceled;
    }

    private AcquirerSaleCancellationResult toResult(OffsetDateTime finishedAt) {
      return AcquirerSaleCancellationResult.builder()
        .trigger(trigger)
        .adjustmentsAnalyzed(adjustmentsAnalyzed)
        .fullCancellationsIdentified(fullCancellationsIdentified)
        .acquirerSalesCanceled(acquirerSalesCanceled)
        .erpSalesCanceled(erpSalesCanceled)
        .acquirerInstallmentsCanceled(acquirerInstallmentsCanceled)
        .erpInstallmentsCanceled(erpInstallmentsCanceled)
        .skippedPartialCancellations(skippedPartialCancellations)
        .skippedWithoutTransaction(skippedWithoutTransaction)
        .skippedAlreadyCanceled(skippedAlreadyCanceled)
        .startedAt(startedAt)
        .finishedAt(finishedAt)
        .build();
    }
  }

  private static class BatchResult {
    private int adjustmentsAnalyzed;
    private int fullCancellationsIdentified;
    private int acquirerSalesCanceled;
    private int erpSalesCanceled;
    private int acquirerInstallmentsCanceled;
    private int erpInstallmentsCanceled;
    private int skippedPartialCancellations;
    private int skippedWithoutTransaction;
    private int skippedAlreadyCanceled;
  }
}
