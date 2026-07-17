package com.cardsync.core.reconciliation.summary;

import com.cardsync.core.conciliation.ReconciliationSettingsService;
import com.cardsync.core.config.ImplantationDateProvider;
import com.cardsync.domain.model.enums.FinancialReconciliationTriggerType;
import com.cardsync.domain.model.enums.StatusReconciliationEnum;
import com.cardsync.domain.model.enums.StatusTransactionEnum;
import com.cardsync.domain.repository.SalesSummaryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Etapa 1b — Resumo de vendas x TransactionAcq.
 *
 * <p>Executada logo após a conciliação ERP x ADQ, atualiza o campo
 * {@code transactionsStatus} de cada {@code SalesSummaryEntity} com base no
 * estado agregado das transações ACQ vinculadas:
 *
 * <ul>
 *   <li>Transações {@code CANCELED} e {@code DELETED} são ignoradas;</li>
 *   <li>Se todas as transações válidas estiverem conciliadas (AUTOMATICALLY ou MANUALLY) → {@code RECONCILED};</li>
 *   <li>Se alguma estiver conciliada, mas não todas → {@code PARTIALLY_RECONCILED};</li>
 *   <li>Se nenhuma estiver conciliada → {@code PENDING};</li>
 *   <li>Resumos sem nenhuma transação vinculada → {@code RECONCILED} (nada a conciliar).</li>
 * </ul>
 *
 * <p>A configuração {@code file-processing.reconciliation.reprocess-sales-summary-transactions}
 * controla se resumos já conciliados devem ser reprocessados.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SalesSummaryTransactionReconciliationService {

  private static final int BATCH_SIZE = 1_000;

  private static final List<Integer> PENDING_STATUSES = List.of(
    StatusReconciliationEnum.PENDING.getCode(),
    StatusReconciliationEnum.PARTIALLY_RECONCILED.getCode()
  );

  private static final List<Integer> EXCLUDED_STATUSES = List.of(
    StatusTransactionEnum.CANCELED.getCode(),
    StatusTransactionEnum.DELETED.getCode()
  );

  private static final List<Integer> RECONCILED_STATUSES = List.of(
    StatusTransactionEnum.AUTOMATICALLY_RECONCILED.getCode(),
    StatusTransactionEnum.MANUALLY_RECONCILED.getCode()
  );

  private final ImplantationDateProvider implantationDateProvider;
  private final ReconciliationSettingsService reconciliationSettingsService;
  private final SalesSummaryRepository salesSummaryRepository;

  @Transactional
  public SalesSummaryTransactionReconciliationResult reconcile(FinancialReconciliationTriggerType trigger) {
    OffsetDateTime startedAt = OffsetDateTime.now();

    boolean includeAll = reconciliationSettingsService.isReprocessSalesSummaryTransactions();

    log.info(
      "📌 Etapa 1b - Resumo x TransactionAcq iniciada. trigger={}, includeAll={}, excludedStatuses={}, reconciledStatuses={}",
      trigger, includeAll, EXCLUDED_STATUSES, RECONCILED_STATUSES
    );

    OffsetDateTime queryStartedAt = OffsetDateTime.now();

    LocalDate implantationDate = implantationDateProvider.get();
    LocalDate lookbackDate = LocalDate.now().minusMonths(reconciliationSettingsService.getReconciliationLookbackMonths());

    List<SalesSummaryTransactionStats> stats = salesSummaryRepository.findStatsForSalesSummaryTransactionReconciliation(
      includeAll,
      PENDING_STATUSES,
      EXCLUDED_STATUSES,
      RECONCILED_STATUSES,
      implantationDate,
      lookbackDate
    );

    log.info(
      "🔎 Etapa 1b - Consulta agregada concluída. trigger={}, resumosCandidatos={}, duração={}s",
      trigger, stats.size(), Duration.between(queryStartedAt, OffsetDateTime.now()).toSeconds()
    );

    List<UUID> reconciledIds = new ArrayList<>();
    List<UUID> partialIds = new ArrayList<>();
    List<UUID> pendingIds = new ArrayList<>();

    int summariesAllExcluded = 0;

    for (SalesSummaryTransactionStats row : stats) {
      if (row.isAllExcluded()) {
        reconciledIds.add(row.getSalesSummaryId());
        summariesAllExcluded++;
        continue;
      }

      if (row.isFullyReconciled()) {
        reconciledIds.add(row.getSalesSummaryId());
        continue;
      }

      if (row.isPartiallyReconciled()) {
        partialIds.add(row.getSalesSummaryId());
        continue;
      }

      pendingIds.add(row.getSalesSummaryId());
    }

    log.info(
      "🧮 Etapa 1b - Classificação concluída. trigger={}, resumos={}, reconciled={} (allExcluded={}), partial={}, pending={}",
      trigger, stats.size(), reconciledIds.size(), summariesAllExcluded, partialIds.size(), pendingIds.size()
    );

    int updatedReconciled = bulkUpdate(reconciledIds, StatusReconciliationEnum.RECONCILED.getCode(), "conciliado", trigger);
    int updatedPartial = bulkUpdate(partialIds, StatusReconciliationEnum.PARTIALLY_RECONCILED.getCode(), "conciliado parcial", trigger);
    int updatedPending = bulkUpdate(pendingIds, StatusReconciliationEnum.PENDING.getCode(), "pendente", trigger);

    int updatedNoTransactions = salesSummaryRepository.markSummariesWithoutTransactionsAsReconciled(
      includeAll,
      PENDING_STATUSES,
      StatusReconciliationEnum.RECONCILED.getCode()
    );

    log.info(
      "🔗 Etapa 1b - Resumos sem transações marcados como conciliados. trigger={}, atualizados={}",
      trigger, updatedNoTransactions
    );

    OffsetDateTime finishedAt = OffsetDateTime.now();

    SalesSummaryTransactionReconciliationResult result = SalesSummaryTransactionReconciliationResult.builder()
      .trigger(trigger)
      .summariesAnalyzed(stats.size())
      .summariesReconciled(reconciledIds.size())
      .summariesPartiallyReconciled(partialIds.size())
      .summariesPending(pendingIds.size())
      .summariesAllExcluded(summariesAllExcluded)
      .summariesWithoutTransactions(updatedNoTransactions)
      .startedAt(startedAt)
      .finishedAt(finishedAt)
      .build();

    log.info(
      "✅ Etapa 1b - Resumo x TransactionAcq finalizada. trigger={}, analisados={}, conciliados={}, parciais={}, pendentes={}, todosCanceladosDeletados={}, semTransacoes={}, updatesConciliado={}, updatesParcial={}, updatesPendente={}, duração={}s",
      result.getTrigger(),
      result.getSummariesAnalyzed(),
      result.getSummariesReconciled(),
      result.getSummariesPartiallyReconciled(),
      result.getSummariesPending(),
      result.getSummariesAllExcluded(),
      result.getSummariesWithoutTransactions(),
      updatedReconciled,
      updatedPartial,
      updatedPending,
      Duration.between(startedAt, finishedAt).toSeconds()
    );

    return result;
  }

  /**
   * Recalcula o transactionsStatus de SalesSummary específicos, ignorando o filtro de
   * lookback usado pela Etapa 1b em lote. Chamado logo após ações manuais que mudam o
   * statusTransaction de uma TransactionAcqEntity fora da esteira automática (ex.:
   * ErpAcquirerResolutionService.reconcileManually, ConciliationWaitingService.createErpFromAcquirer)
   * — sem isso, o resumo vinculado ficaria com o status antigo (ex.: "Pendente") para
   * sempre, caso já tenha saído da janela de lookback da esteira automática.
   */
  @Transactional
  public void recalculateForSalesSummaryIds(Collection<UUID> salesSummaryIds) {
    List<UUID> ids = salesSummaryIds == null
      ? List.of()
      : salesSummaryIds.stream().filter(java.util.Objects::nonNull).distinct().toList();

    if (ids.isEmpty()) {
      return;
    }

    List<SalesSummaryTransactionStats> stats = salesSummaryRepository
      .findStatsForSalesSummaryTransactionReconciliationByIds(ids, EXCLUDED_STATUSES, RECONCILED_STATUSES);

    List<UUID> reconciledIds = new ArrayList<>();
    List<UUID> partialIds = new ArrayList<>();
    List<UUID> pendingIds = new ArrayList<>();

    for (SalesSummaryTransactionStats row : stats) {
      if (row.isAllExcluded() || row.isFullyReconciled()) {
        reconciledIds.add(row.getSalesSummaryId());
      } else if (row.isPartiallyReconciled()) {
        partialIds.add(row.getSalesSummaryId());
      } else {
        pendingIds.add(row.getSalesSummaryId());
      }
    }

    bulkUpdate(reconciledIds, StatusReconciliationEnum.RECONCILED.getCode(), "conciliado (recálculo pontual)", FinancialReconciliationTriggerType.MANUAL);
    bulkUpdate(partialIds, StatusReconciliationEnum.PARTIALLY_RECONCILED.getCode(), "conciliado parcial (recálculo pontual)", FinancialReconciliationTriggerType.MANUAL);
    bulkUpdate(pendingIds, StatusReconciliationEnum.PENDING.getCode(), "pendente (recálculo pontual)", FinancialReconciliationTriggerType.MANUAL);
  }

  private int bulkUpdate(
    List<UUID> ids,
    Integer status,
    String label,
    FinancialReconciliationTriggerType trigger
  ) {
    if (ids.isEmpty()) {
      log.info("ℹ️ Etapa 1b - Nenhum SalesSummary para atualizar como {}. trigger={}", label, trigger);
      return 0;
    }

    OffsetDateTime startedAt = OffsetDateTime.now();
    int updated = 0;
    int totalBatches = (int) Math.ceil(ids.size() / (double) BATCH_SIZE);

    log.info(
      "💾 Etapa 1b - Atualizando SalesSummary como {}. trigger={}, total={}, batches={}",
      label, trigger, ids.size(), totalBatches
    );

    for (int start = 0, batch = 1; start < ids.size(); start += BATCH_SIZE, batch++) {
      List<UUID> batchIds = ids.subList(start, Math.min(start + BATCH_SIZE, ids.size()));
      int batchUpdated = salesSummaryRepository.updateTransactionsStatusByIds(batchIds, status);
      updated += batchUpdated;

      log.info(
        "🔄 Etapa 1b - Batch {}/{} concluído. label={}, ids={}, atualizados={}, totalAtualizados={}",
        batch, totalBatches, label, batchIds.size(), batchUpdated, updated
      );
    }

    log.info(
      "✅ Etapa 1b - Updates concluídos para {}. trigger={}, atualizados={}, duração={}s",
      label, trigger, updated, Duration.between(startedAt, OffsetDateTime.now()).toSeconds()
    );

    return updated;
  }
}
