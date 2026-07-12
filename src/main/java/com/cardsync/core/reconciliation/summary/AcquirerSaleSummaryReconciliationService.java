package com.cardsync.core.reconciliation.summary;

import com.cardsync.core.conciliation.ReconciliationSettingsService;
import com.cardsync.core.config.ImplantationDateProvider;
import com.cardsync.domain.model.enums.FeeReconciliationStatusEnum;
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
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AcquirerSaleSummaryReconciliationService {

  private static final int UPDATE_BATCH_SIZE = 1_000;

  private static final List<Integer> PENDING_SUMMARY_TRANSACTION_STATUSES = List.of(
    StatusReconciliationEnum.PENDING.getCode(),
    StatusReconciliationEnum.PARTIALLY_RECONCILED.getCode()
  );

  private static final List<Integer> ELIGIBLE_SALE_STATUSES = List.of(
    StatusTransactionEnum.AUTOMATICALLY_RECONCILED.getCode(),
    StatusTransactionEnum.MANUALLY_RECONCILED.getCode(),
    StatusTransactionEnum.CANCELED.getCode(),
    StatusTransactionEnum.DELETED.getCode()
  );

  private static final List<Integer> ELIGIBLE_FEE_STATUSES = List.of(
    FeeReconciliationStatusEnum.RECONCILED.getCode(),
    FeeReconciliationStatusEnum.MISSING_VALID_CONTRACT.getCode()
  );

  private final ImplantationDateProvider implantationDateProvider;
  private final ReconciliationSettingsService reconciliationSettingsService;
  private final SalesSummaryRepository salesSummaryRepository;

  /**
   * Etapa 3 - Venda ADQ x resumo.

   * Versão otimizada:
   * - antes: buscava os summaries e depois fazia 1 consulta por summary para carregar transações;
   * - agora: o banco calcula total/elegíveis por SalesSummary em uma única consulta agregada;
   * - depois são feitos updates em lote por status final.
   */
  @Transactional
  public AcquirerSaleSummaryReconciliationResult reconcilePending(FinancialReconciliationTriggerType trigger) {
    OffsetDateTime startedAt = OffsetDateTime.now();

    boolean reprocess = reconciliationSettingsService.isReprocessAcquirerSaleSummary();

    log.info(
      "📌 Etapa 3 - Venda ADQ x resumo iniciada. trigger={}, pendingSummaryStatuses={}, eligibleSaleStatuses={}, eligibleFeeStatuses={}, updateBatchSize={}, reprocess={}",
      trigger,
      PENDING_SUMMARY_TRANSACTION_STATUSES,
      ELIGIBLE_SALE_STATUSES,
      ELIGIBLE_FEE_STATUSES,
      UPDATE_BATCH_SIZE,
      reprocess
    );

    OffsetDateTime queryStartedAt = OffsetDateTime.now();
    LocalDate implantationDate = implantationDateProvider.get();
    LocalDate lookbackDate = LocalDate.now().minusMonths(reconciliationSettingsService.getReconciliationLookbackMonths());

    List<AcquirerSaleSummaryStats> stats = salesSummaryRepository.findStatsForAcquirerSaleSummaryReconciliation(
      reprocess,
      PENDING_SUMMARY_TRANSACTION_STATUSES,
      ELIGIBLE_SALE_STATUSES,
      ELIGIBLE_FEE_STATUSES,
      implantationDate,
      lookbackDate
    );

    log.info(
      "🔎 Etapa 3 - Consulta agregada concluída. trigger={}, summariesCandidatos={}, duraçãoConsulta={}s",
      trigger,
      stats.size(),
      Duration.between(queryStartedAt, OffsetDateTime.now()).toSeconds()
    );

    Counter counter = new Counter(trigger, startedAt);

    List<UUID> reconciledIds = new ArrayList<>();
    List<UUID> partialIds = new ArrayList<>();
    List<UUID> pendingIds = new ArrayList<>();

    for (AcquirerSaleSummaryStats row : stats) {
      counter.summariesAnalyzed++;
      counter.transactionsAnalyzed += row.totalTransactionsAsInt();
      counter.transactionsEligible += row.eligibleTransactionsAsInt();

      if (row.isFullyEligible()) {
        reconciledIds.add(row.getSalesSummaryId());
        counter.summariesReconciled++;
        continue;
      }

      if (row.isPartiallyEligible()) {
        partialIds.add(row.getSalesSummaryId());
        counter.summariesPartiallyReconciled++;
        continue;
      }

      pendingIds.add(row.getSalesSummaryId());
      counter.summariesBlockedByPreviousStep++;
      counter.summariesPending++;
    }

    log.info(
      "🧮 Etapa 3 - Classificação concluída. trigger={}, summaries={}, reconciled={}, partial={}, pending={}, txAnalisadas={}, txElegiveis={}",
      trigger,
      stats.size(),
      reconciledIds.size(),
      partialIds.size(),
      pendingIds.size(),
      counter.transactionsAnalyzed,
      counter.transactionsEligible
    );

    int updatedReconciled = bulkUpdateStatus(
      reconciledIds,
      StatusReconciliationEnum.RECONCILED.getCode(),
      "conciliado",
      trigger
    );

    int updatedPartial = bulkUpdateStatus(
      partialIds,
      StatusReconciliationEnum.PARTIALLY_RECONCILED.getCode(),
      "conciliado parcial",
      trigger
    );

    int updatedPending = bulkUpdateStatus(
      pendingIds,
      StatusReconciliationEnum.PENDING.getCode(),
      "pendente",
      trigger
    );

    int updatedNoTransactions = salesSummaryRepository.markSummariesWithoutTransactionsAsReconciled(
      reprocess,
      PENDING_SUMMARY_TRANSACTION_STATUSES,
      StatusReconciliationEnum.RECONCILED.getCode()
    );
    counter.summariesWithoutTransactions = updatedNoTransactions;

    log.info(
      "🔗 Etapa 3 - SalesSummary sem transações marcados como conciliados. trigger={}, atualizados={}",
      trigger,
      updatedNoTransactions
    );

    OffsetDateTime finishedAt = OffsetDateTime.now();
    AcquirerSaleSummaryReconciliationResult result = counter.toResult(finishedAt);

    log.info(
      "✅ Etapa 3 - Venda ADQ x resumo finalizada. trigger={}, summariesAnalisados={}, conciliados={}, parciais={}, pendentes={}, bloqueados={}, semTransacoes={}, transactionsAnalisadas={}, elegiveis={}, updatesConciliado={}, updatesParcial={}, updatesPendente={}, duraçãoTotal={}s",
      result.getTrigger(),
      result.getSummariesAnalyzed(),
      result.getSummariesReconciled(),
      result.getSummariesPartiallyReconciled(),
      result.getSummariesPending(),
      result.getSummariesBlockedByPreviousStep(),
      result.getSummariesWithoutTransactions(),
      result.getTransactionsAnalyzed(),
      result.getTransactionsEligible(),
      updatedReconciled,
      updatedPartial,
      updatedPending,
      Duration.between(startedAt, finishedAt).toSeconds()
    );

    return result;
  }

  private int bulkUpdateStatus(
    List<UUID> ids,
    Integer status,
    String label,
    FinancialReconciliationTriggerType trigger
  ) {
    if (ids.isEmpty()) {
      log.info(
        "ℹ️ Etapa 3 - Nenhum SalesSummary para atualizar como {}. trigger={}, status={}",
        label,
        trigger,
        status
      );
      return 0;
    }

    OffsetDateTime startedAt = OffsetDateTime.now();
    int updated = 0;
    int totalBatches = (int) Math.ceil(ids.size() / (double) UPDATE_BATCH_SIZE);

    log.info(
      "💾 Etapa 3 - Atualizando SalesSummary como {}. trigger={}, status={}, total={}, batches={}",
      label,
      trigger,
      status,
      ids.size(),
      totalBatches
    );

    for (int start = 0, batch = 1; start < ids.size(); start += UPDATE_BATCH_SIZE, batch++) {
      List<UUID> batchIds = ids.subList(start, Math.min(start + UPDATE_BATCH_SIZE, ids.size()));
      int batchUpdated = salesSummaryRepository.updateTransactionsStatusByIds(batchIds, status);
      updated += batchUpdated;

      log.info(
        "🔄 Etapa 3 - Update batch {}/{} concluído. label={}, ids={}, atualizados={}, totalAtualizados={}",
        batch,
        totalBatches,
        label,
        batchIds.size(),
        batchUpdated,
        updated
      );
    }

    log.info(
      "✅ Etapa 3 - Updates concluídos para {}. trigger={}, atualizados={}, duração={}s",
      label,
      trigger,
      updated,
      Duration.between(startedAt, OffsetDateTime.now()).toSeconds()
    );

    return updated;
  }

  private static class Counter {
    private final FinancialReconciliationTriggerType trigger;
    private final OffsetDateTime startedAt;
    private int summariesAnalyzed;
    private int summariesReconciled;
    private int summariesPartiallyReconciled;
    private int summariesPending;
    private int summariesBlockedByPreviousStep;
    private int transactionsAnalyzed;
    private int transactionsEligible;
    private int summariesWithoutTransactions;

    private Counter(FinancialReconciliationTriggerType trigger, OffsetDateTime startedAt) {
      this.trigger = trigger;
      this.startedAt = startedAt;
    }

    private AcquirerSaleSummaryReconciliationResult toResult(OffsetDateTime finishedAt) {
      return AcquirerSaleSummaryReconciliationResult.builder()
        .trigger(trigger)
        .summariesAnalyzed(summariesAnalyzed)
        .summariesReconciled(summariesReconciled)
        .summariesPartiallyReconciled(summariesPartiallyReconciled)
        .summariesPending(summariesPending)
        .summariesBlockedByPreviousStep(summariesBlockedByPreviousStep)
        .transactionsAnalyzed(transactionsAnalyzed)
        .transactionsEligible(transactionsEligible)
        .summariesWithoutTransactions(summariesWithoutTransactions)
        .startedAt(startedAt)
        .finishedAt(finishedAt)
        .build();
    }
  }
}
