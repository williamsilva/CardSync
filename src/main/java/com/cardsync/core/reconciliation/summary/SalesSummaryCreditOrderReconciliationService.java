package com.cardsync.core.reconciliation.summary;

import com.cardsync.core.conciliation.ReconciliationSettingsService;
import com.cardsync.core.config.ImplantationDateProvider;
import com.cardsync.domain.model.AnticipationEntity;
import com.cardsync.domain.model.CreditOrderEntity;
import com.cardsync.domain.model.SalesSummaryEntity;
import com.cardsync.domain.model.enums.*;
import com.cardsync.domain.repository.AdjustmentRepository;
import com.cardsync.domain.repository.AnticipationRepository;
import com.cardsync.domain.repository.CreditOrderRepository;
import com.cardsync.domain.repository.SalesSummaryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SalesSummaryCreditOrderReconciliationService {

  private static final int UPDATE_BATCH_SIZE = 1_000;
  private static final int GENERATION_BATCH_SIZE = 1_000;

  private static final int ORDER_SUMMARY_PENDING = StatusReconciliationEnum.PENDING.getCode();
  private static final int ORDER_SUMMARY_RECONCILED = StatusReconciliationEnum.RECONCILED.getCode();
  private static final int ORDER_SUMMARY_PARTIALLY_RECONCILED = StatusReconciliationEnum.PARTIALLY_RECONCILED.getCode();

  /**
   * Etapa 5 - Resumo x ordem de pagamento.
   *
   * A conciliação com ordem de crédito é independente do estado das transações ADQ.
   * Um resumo pode ter (ou receber) uma ordem de crédito mesmo que suas transações
   * individuais ainda estejam parcialmente pendentes. São elegíveis resumos com
   * {@code transactionsStatus} RECONCILED ou PARTIALLY_RECONCILED. Resumos com
   * {@code transactionsStatus} NULL (ainda não processados pela Etapa 2) são incluídos
   * via condição adicional na query do repositório.
   */
  private static final List<Integer> ELIGIBLE_TRANSACTION_SUMMARY_STATUSES = List.of(
    StatusReconciliationEnum.RECONCILED.getCode(),
    StatusReconciliationEnum.PARTIALLY_RECONCILED.getCode()
  );

  private static final List<Integer> PENDING_SUMMARY_CREDIT_ORDER_STATUSES = List.of(
    StatusReconciliationEnum.PENDING.getCode(),
    StatusReconciliationEnum.PARTIALLY_RECONCILED.getCode()
  );

  private final ImplantationDateProvider implantationDateProvider;
  private final ReconciliationSettingsService reconciliationSettingsService;
  private final SalesSummaryRepository salesSummaryRepository;
  private final CreditOrderRepository creditOrderRepository;
  private final AnticipationRepository anticipationRepository;
  private final AdjustmentRepository adjustmentRepository;

  @Transactional
  public SalesSummaryCreditOrderReconciliationResult reconcilePending(FinancialReconciliationTriggerType trigger) {
    return reconcilePending(trigger, false);
  }

  /**
   * @param ignoreLookback quando {@code true}, ignora o filtro de rvDate/lookback — usado
   *                        para um backfill único, reavaliando resumos antigos que já saíram
   *                        da janela normal de lookback.
   */
  @Transactional
  public SalesSummaryCreditOrderReconciliationResult reconcilePending(FinancialReconciliationTriggerType trigger, boolean ignoreLookback) {
    OffsetDateTime startedAt = OffsetDateTime.now();

    boolean reprocess = reconciliationSettingsService.isReprocessSalesSummaryCreditOrder();

    log.info(
      "📌 Etapa 4 - Resumo x ordem iniciada. trigger={}, eligibleTransactionStatuses={}, pendingCreditOrderStatuses={}, updateBatchSize={}, generationBatchSize={}, reprocess={}, ignoreLookback={}",
      trigger,
      ELIGIBLE_TRANSACTION_SUMMARY_STATUSES,
      PENDING_SUMMARY_CREDIT_ORDER_STATUSES,
      UPDATE_BATCH_SIZE,
      GENERATION_BATCH_SIZE,
      reprocess,
      ignoreLookback
    );

    OffsetDateTime queryStartedAt = OffsetDateTime.now();

    LocalDate implantationDate = implantationDateProvider.get();
    LocalDate lookbackDate = LocalDate.now().minusMonths(reconciliationSettingsService.getReconciliationLookbackMonths());

    List<SalesSummaryCreditOrderStats> stats = ignoreLookback
      ? salesSummaryRepository.findStatsForSalesSummaryCreditOrderReconciliationIgnoringLookback(
          reprocess,
          ELIGIBLE_TRANSACTION_SUMMARY_STATUSES,
          PENDING_SUMMARY_CREDIT_ORDER_STATUSES,
          implantationDate
        )
      : salesSummaryRepository.findStatsForSalesSummaryCreditOrderReconciliation(
          reprocess,
          ELIGIBLE_TRANSACTION_SUMMARY_STATUSES,
          PENDING_SUMMARY_CREDIT_ORDER_STATUSES,
          implantationDate,
          lookbackDate
        );

    log.info(
      "🔎 Etapa 4 - Consulta agregada concluída. trigger={}, summariesCandidatos={}, duraçãoConsulta={}s",
      trigger,
      stats.size(),
      Duration.between(queryStartedAt, OffsetDateTime.now()).toSeconds()
    );

    return processStats(trigger, stats, startedAt, ignoreLookback, lookbackDate);
  }

  /**
   * Backfill dedicado: SalesSummary de vendas ANTERIORES à implantação ({@code rvDate <
   * implantationDate}) — nunca avaliadas pelo fluxo normal acima, mesmo quando alguma parcela
   * (CreditOrder) só vence/libera bem depois do go-live (ver
   * SalesSummaryRepository#findStatsForSalesSummaryCreditOrderReconciliationPreImplantation).
   * Encontrado ao investigar por que milhares de CreditOrder pós go-live nunca ficam elegíveis
   * pra conciliação bancária (Etapa 6 do matcher): o SalesSummary da venda original (pré-go-live)
   * nunca tem creditOrderStatus recalculado, então a parcela nunca sai de salesSummaryStatus
   * PENDENTE — apesar de ter releaseDate e lançamento bancário compatível disponível.
   * Reaproveita a mesma classificação/geração sintética/atualização em lote de
   * {@link #reconcilePending}, só muda a origem dos {@code stats} e ignora o filtro de lookback
   * no reparo de consistência final (dado histórico, sem sentido aplicar janela rolante).
   */
  @Transactional
  public SalesSummaryCreditOrderReconciliationResult reconcilePreImplantation(FinancialReconciliationTriggerType trigger) {
    OffsetDateTime startedAt = OffsetDateTime.now();

    boolean reprocess = reconciliationSettingsService.isReprocessSalesSummaryCreditOrder();
    LocalDate implantationDate = implantationDateProvider.get();

    log.info(
      "📌 Etapa 4 (backfill pré-implantação) iniciada. trigger={}, implantationDate={}, reprocess={}",
      trigger, implantationDate, reprocess
    );

    OffsetDateTime queryStartedAt = OffsetDateTime.now();

    List<SalesSummaryCreditOrderStats> stats = salesSummaryRepository.findStatsForSalesSummaryCreditOrderReconciliationPreImplantation(
      reprocess,
      ELIGIBLE_TRANSACTION_SUMMARY_STATUSES,
      PENDING_SUMMARY_CREDIT_ORDER_STATUSES,
      implantationDate
    );

    log.info(
      "🔎 Etapa 4 (backfill pré-implantação) - Consulta agregada concluída. trigger={}, summariesCandidatos={}, duraçãoConsulta={}s",
      trigger,
      stats.size(),
      Duration.between(queryStartedAt, OffsetDateTime.now()).toSeconds()
    );

    return processStats(trigger, stats, startedAt, true, null);
  }

  private SalesSummaryCreditOrderReconciliationResult processStats(
    FinancialReconciliationTriggerType trigger,
    List<SalesSummaryCreditOrderStats> stats,
    OffsetDateTime startedAt,
    boolean ignoreLookback,
    LocalDate lookbackDate
  ) {
    long orphanCreditOrders = creditOrderRepository.countWithoutSalesSummary();
    if (orphanCreditOrders > 0) {
      log.warn(
        "⚠️ Etapa 4 - Diagnóstico: {} CreditOrder(s) sem salesSummary vinculado. Esses registros não participam da conciliação e podem indicar falha no processamento dos arquivos de ordem de crédito (RV/PV sem match com SalesSummary).",
        orphanCreditOrders
      );
    } else {
      log.info("✅ Etapa 4 - Diagnóstico: nenhuma CreditOrder órfã (sem salesSummary). trigger={}", trigger);
    }

    Counter counter = new Counter(trigger, startedAt);
    counter.summariesAnalyzed = stats.size();

    Map<UUID, BigDecimal> grossValueMap = new java.util.HashMap<>(stats.size());
    List<UUID> summariesWithOrders = new ArrayList<>();
    List<UUID> summariesPartiallyReconciled = new ArrayList<>();
    List<UUID> summariesWithoutOrders = new ArrayList<>();
    long existingCreditOrders = 0L;

    for (SalesSummaryCreditOrderStats row : stats) {
      grossValueMap.put(row.getSalesSummaryId(), row.getGrossValue() != null ? row.getGrossValue() : BigDecimal.ZERO);
      long ordersCount = row.creditOrdersCountSafe();
      existingCreditOrders += ordersCount;

      if (ordersCount == 0) {
        summariesWithoutOrders.add(row.getSalesSummaryId());
      } else if (row.isFullyReconciled()) {
        summariesWithOrders.add(row.getSalesSummaryId());
      } else {
        summariesPartiallyReconciled.add(row.getSalesSummaryId());
      }
    }

    counter.creditOrdersAnalyzed = safeInt(existingCreditOrders);

    log.info(
      "🧮 Etapa 4 - Classificação inicial concluída. trigger={}, summaries={}, totalmenteConciliados={}, parcialmenteConciliados={}, semOrdens={}, ordensExistentes={}",
      trigger,
      stats.size(),
      summariesWithOrders.size(),
      summariesPartiallyReconciled.size(),
      summariesWithoutOrders.size(),
      existingCreditOrders
    );

    GeneratedOrders generated = generateSyntheticOrders(trigger, summariesWithoutOrders);
    int anticipationGeneratedOrders = generateSyntheticOrdersFromAnticipations(trigger);

    counter.generatedCreditOrders = generated.generatedOrders() + anticipationGeneratedOrders;
    counter.creditOrdersAnalyzed += generated.generatedOrders() + anticipationGeneratedOrders;
    counter.summariesWithoutCreditOrders = generated.notGeneratedSummaryIds().size();
    counter.summariesPending = generated.notGeneratedSummaryIds().size();
    counter.summariesReconciled = summariesWithOrders.size() + generated.generatedSummaryIds().size();
    counter.summariesPartiallyReconciled = summariesPartiallyReconciled.size();
    counter.summariesBlockedByPreviousStep = 0;

    List<UUID> reconciledSummaryIds = new ArrayList<>(summariesWithOrders.size() + generated.generatedSummaryIds().size());
    reconciledSummaryIds.addAll(summariesWithOrders);
    reconciledSummaryIds.addAll(generated.generatedSummaryIds());

    counter.totalGrossValueReconciled = reconciledSummaryIds.stream()
      .map(id -> grossValueMap.getOrDefault(id, BigDecimal.ZERO))
      .reduce(BigDecimal.ZERO, BigDecimal::add);

    counter.totalGrossValuePending = generated.notGeneratedSummaryIds().stream()
      .map(id -> grossValueMap.getOrDefault(id, BigDecimal.ZERO))
      .reduce(BigDecimal.ZERO, BigDecimal::add);

    List<UUID> summariesWithAnyOrders = new ArrayList<>(summariesWithOrders.size() + summariesPartiallyReconciled.size());
    summariesWithAnyOrders.addAll(summariesWithOrders);
    summariesWithAnyOrders.addAll(summariesPartiallyReconciled);

    bulkUpdateExistingCreditOrders(trigger, summariesWithAnyOrders);
    bulkUpdateSalesSummaryStatuses(trigger, "conciliado", reconciledSummaryIds, ORDER_SUMMARY_RECONCILED);
    bulkUpdateSalesSummaryStatuses(trigger, "parcialmente conciliado", summariesPartiallyReconciled, ORDER_SUMMARY_PARTIALLY_RECONCILED);
    bulkUpdateSalesSummaryStatuses(trigger, "pendente/sem ordem", generated.notGeneratedSummaryIds(), ORDER_SUMMARY_PENDING);
    bulkUpdateManualGenerated(trigger, generated.generatedSummaryIds());

    int fixedOrders = ignoreLookback
      ? creditOrderRepository.syncSalesSummaryStatusForReconciledSummariesIgnoringLookback(ORDER_SUMMARY_RECONCILED)
      : creditOrderRepository.syncSalesSummaryStatusForReconciledSummaries(ORDER_SUMMARY_RECONCILED, lookbackDate);
    if (fixedOrders > 0) {
      log.warn(
        "⚠️ Etapa 4 - Consistência: {} ordem(ns) de crédito com salesSummaryStatus inconsistente detectada(s) e corrigida(s). trigger={}",
        fixedOrders,
        trigger
      );
    }

    SalesSummaryCreditOrderReconciliationResult result = counter.toResult(OffsetDateTime.now());

    log.info(
      "✅ Etapa 4 - Resumo x ordem finalizada. trigger={}, summariesAnalisados={}, conciliados={}, parciais={}, pendentes={}, bloqueados={}, semOrdens={}, ordensGeradas={}, ordensAnalisadas={}, duraçãoTotal={}s",
      result.getTrigger(),
      result.getSummariesAnalyzed(),
      result.getSummariesReconciled(),
      result.getSummariesPartiallyReconciled(),
      result.getSummariesPending(),
      result.getSummariesBlockedByPreviousStep(),
      result.getSummariesWithoutCreditOrders(),
      result.getGeneratedCreditOrders(),
      result.getCreditOrdersAnalyzed(),
      Duration.between(startedAt, result.getFinishedAt()).toSeconds()
    );

    return result;
  }

  private GeneratedOrders generateSyntheticOrders(FinancialReconciliationTriggerType trigger, List<UUID> summariesWithoutOrders) {
    if (summariesWithoutOrders.isEmpty()) {
      log.info("ℹ️ Etapa 4 - Nenhum SalesSummary sem ordem para avaliar geração sintética. trigger={}", trigger);
      return new GeneratedOrders(List.of(), List.of(), 0);
    }

    OffsetDateTime startedAt = OffsetDateTime.now();
    List<UUID> generatedSummaryIds = new ArrayList<>();
    List<UUID> notGeneratedSummaryIds = new ArrayList<>();
    int generatedOrders = 0;

    int totalBatches = totalBatches(summariesWithoutOrders.size(), GENERATION_BATCH_SIZE);

    log.info(
      "🧾 Etapa 4 - Avaliando geração de ordens sintéticas. trigger={}, summariesSemOrdem={}, batches={}",
      trigger,
      summariesWithoutOrders.size(),
      totalBatches
    );

    List<SalesSummaryEntity> notGeneratedSummaries = new ArrayList<>();

    int batchNumber = 0;
    for (List<UUID> batchIds : partition(summariesWithoutOrders, GENERATION_BATCH_SIZE)) {
      batchNumber++;
      OffsetDateTime batchStartedAt = OffsetDateTime.now();

      List<SalesSummaryEntity> summaries = salesSummaryRepository.findBatchForSalesSummaryCreditOrderReconciliation(batchIds);
      List<CreditOrderEntity> ordersToGenerate = new ArrayList<>();

      Map<UUID, BigDecimal> debitAdjustmentsBySummaryId = new java.util.HashMap<>();
      for (Object[] row : adjustmentRepository.sumDebitAdjustmentsBySalesSummaryIdIn(batchIds)) {
        debitAdjustmentsBySummaryId.put((UUID) row[0], (BigDecimal) row[1]);
      }

      for (SalesSummaryEntity summary : summaries) {
        if (shouldGenerateSyntheticCreditOrder(summary)) {
          BigDecimal debitAdjustments = debitAdjustmentsBySummaryId.getOrDefault(summary.getId(), BigDecimal.ZERO);
          ordersToGenerate.add(generateSyntheticCreditOrder(summary, debitAdjustments));
          generatedSummaryIds.add(summary.getId());
        } else {
          notGeneratedSummaryIds.add(summary.getId());
          notGeneratedSummaries.add(summary);
        }
      }

      if (!ordersToGenerate.isEmpty()) {
        creditOrderRepository.saveAll(ordersToGenerate);
        generatedOrders += ordersToGenerate.size();
      }

      log.info(
        "🔄 Etapa 4 - Geração sintética batch {}/{} concluída. ids={}, geradas={}, naoGeradas={}, totalGeradas={}, duração={}s",
        batchNumber,
        totalBatches,
        batchIds.size(),
        ordersToGenerate.size(),
        batchIds.size() - ordersToGenerate.size(),
        generatedOrders,
        Duration.between(batchStartedAt, OffsetDateTime.now()).toSeconds()
      );
    }

    log.info(
      "✅ Etapa 4 - Geração sintética concluída. trigger={}, summariesSemOrdem={}, ordensGeradas={}, summariesSemGeracao={}, duração={}s",
      trigger,
      summariesWithoutOrders.size(),
      generatedOrders,
      notGeneratedSummaryIds.size(),
      Duration.between(startedAt, OffsetDateTime.now()).toSeconds()
    );

    logNotGeneratedDistribution(notGeneratedSummaries, trigger);
    logPvMismatchDiagnosis(notGeneratedSummaries, trigger);

    return new GeneratedOrders(generatedSummaryIds, notGeneratedSummaryIds, generatedOrders);
  }

  /**
   * Antecipações (Rede EEFI "036" — ver ProcessRedeEeFiService.buildAnticipation) representam
   * parcelas específicas de uma RV adiantadas pelo banco antes do vencimento normal: o valor cai
   * na conta, mas nunca gera CreditOrder nenhuma, então nunca participa da conciliação bancária
   * (Etapa 6). generatedOrders controla a idempotência — uma vez gerada a ordem sintética, a
   * antecipação não é reavaliada de novo. Independente da geração baseada em SalesSummary acima:
   * uma mesma RV pode ter algumas parcelas antecipadas (aqui) e outras liquidadas normalmente (via
   * CreditOrder real ou sintética por SalesSummary) — não é duplicidade, são parcelas diferentes.
   */
  private int generateSyntheticOrdersFromAnticipations(FinancialReconciliationTriggerType trigger) {
    List<UUID> eligibleIds = anticipationRepository.findIdsEligibleForSyntheticCreditOrderGeneration();
    if (eligibleIds.isEmpty()) {
      log.info("ℹ️ Etapa 4 - Nenhuma Anticipation elegível para geração sintética. trigger={}", trigger);
      return 0;
    }

    OffsetDateTime startedAt = OffsetDateTime.now();
    int totalBatches = totalBatches(eligibleIds.size(), GENERATION_BATCH_SIZE);
    int generatedOrders = 0;

    log.info(
      "🧾 Etapa 4 - Avaliando geração sintética a partir de antecipações. trigger={}, antecipacoesElegiveis={}, batches={}",
      trigger,
      eligibleIds.size(),
      totalBatches
    );

    int batchNumber = 0;
    for (List<UUID> batchIds : partition(eligibleIds, GENERATION_BATCH_SIZE)) {
      batchNumber++;
      OffsetDateTime batchStartedAt = OffsetDateTime.now();

      List<AnticipationEntity> anticipations = anticipationRepository.findBatchForSyntheticCreditOrderGeneration(batchIds);
      List<CreditOrderEntity> ordersToGenerate = new ArrayList<>(anticipations.size());

      for (AnticipationEntity anticipation : anticipations) {
        ordersToGenerate.add(generateSyntheticCreditOrder(anticipation));
        anticipation.setGeneratedOrders(true);
      }

      if (!ordersToGenerate.isEmpty()) {
        creditOrderRepository.saveAll(ordersToGenerate);
        anticipationRepository.saveAll(anticipations);
        generatedOrders += ordersToGenerate.size();
      }

      log.info(
        "🔄 Etapa 4 - Geração sintética (antecipação) batch {}/{} concluída. ids={}, geradas={}, totalGeradas={}, duração={}s",
        batchNumber,
        totalBatches,
        batchIds.size(),
        ordersToGenerate.size(),
        generatedOrders,
        Duration.between(batchStartedAt, OffsetDateTime.now()).toSeconds()
      );
    }

    log.info(
      "✅ Etapa 4 - Geração sintética (antecipação) concluída. trigger={}, antecipacoesElegiveis={}, ordensGeradas={}, duração={}s",
      trigger,
      eligibleIds.size(),
      generatedOrders,
      Duration.between(startedAt, OffsetDateTime.now()).toSeconds()
    );

    return generatedOrders;
  }

  private void bulkUpdateExistingCreditOrders(FinancialReconciliationTriggerType trigger, List<UUID> summariesWithOrders) {
    if (summariesWithOrders.isEmpty()) {
      log.info("ℹ️ Etapa 4 - Nenhuma ordem existente para atualizar. trigger={}", trigger);
      return;
    }

    OffsetDateTime startedAt = OffsetDateTime.now();
    int totalBatches = totalBatches(summariesWithOrders.size(), UPDATE_BATCH_SIZE);
    int totalSalesSummaryStatusUpdated = 0;
    int totalPaymentStatusUpdated = 0;
    int totalReconciliationStatusUpdated = 0;

    log.info(
      "💾 Etapa 4 - Atualizando CreditOrder existentes. trigger={}, summariesComOrdens={}, batches={}",
      trigger,
      summariesWithOrders.size(),
      totalBatches
    );

    int batchNumber = 0;
    for (List<UUID> batchIds : partition(summariesWithOrders, UPDATE_BATCH_SIZE)) {
      batchNumber++;
      OffsetDateTime batchStartedAt = OffsetDateTime.now();

      int salesSummaryStatusUpdated = creditOrderRepository.updateSalesSummaryStatusBySalesSummaryIds(
        batchIds,
        ORDER_SUMMARY_RECONCILED
      );
      int paymentStatusUpdated = creditOrderRepository.updateNullStatusPaymentBankBySalesSummaryIds(
        batchIds,
        StatusPaymentBankEnum.PENDING.getCode()
      );
      int reconciliationStatusUpdated = creditOrderRepository.updateNullReconciliationStatusBySalesSummaryIds(
        batchIds,
        StatusPaymentBankEnum.PENDING.getCode()
      );

      totalSalesSummaryStatusUpdated += salesSummaryStatusUpdated;
      totalPaymentStatusUpdated += paymentStatusUpdated;
      totalReconciliationStatusUpdated += reconciliationStatusUpdated;

      log.info(
        "🔄 Etapa 4 - Update CreditOrder batch {}/{} concluído. summaries={}, salesSummaryStatusAtualizados={}, statusPaymentNullAtualizados={}, reconciliationNullAtualizados={}, duração={}s",
        batchNumber,
        totalBatches,
        batchIds.size(),
        salesSummaryStatusUpdated,
        paymentStatusUpdated,
        reconciliationStatusUpdated,
        Duration.between(batchStartedAt, OffsetDateTime.now()).toSeconds()
      );
    }

    log.info(
      "✅ Etapa 4 - Updates de CreditOrder concluídos. trigger={}, salesSummaryStatusAtualizados={}, statusPaymentNullAtualizados={}, reconciliationNullAtualizados={}, duração={}s",
      trigger,
      totalSalesSummaryStatusUpdated,
      totalPaymentStatusUpdated,
      totalReconciliationStatusUpdated,
      Duration.between(startedAt, OffsetDateTime.now()).toSeconds()
    );
  }

  private void bulkUpdateSalesSummaryStatuses(
    FinancialReconciliationTriggerType trigger,
    String label,
    List<UUID> summaryIds,
    Integer status
  ) {
    if (summaryIds.isEmpty()) {
      log.info("ℹ️ Etapa 4 - Nenhum SalesSummary para atualizar como {}. trigger={}", label, trigger);
      return;
    }

    OffsetDateTime startedAt = OffsetDateTime.now();
    int totalBatches = totalBatches(summaryIds.size(), UPDATE_BATCH_SIZE);
    int totalUpdated = 0;

    log.info(
      "💾 Etapa 4 - Atualizando SalesSummary como {}. trigger={}, status={}, total={}, batches={}",
      label,
      trigger,
      status,
      summaryIds.size(),
      totalBatches
    );

    int batchNumber = 0;
    for (List<UUID> batchIds : partition(summaryIds, UPDATE_BATCH_SIZE)) {
      batchNumber++;
      OffsetDateTime batchStartedAt = OffsetDateTime.now();

      int updated = salesSummaryRepository.updateCreditOrderStatusByIds(batchIds, status);
      totalUpdated += updated;

      log.info(
        "🔄 Etapa 4 - Update SalesSummary batch {}/{} concluído. label={}, ids={}, atualizados={}, totalAtualizados={}, duração={}s",
        batchNumber,
        totalBatches,
        label,
        batchIds.size(),
        updated,
        totalUpdated,
        Duration.between(batchStartedAt, OffsetDateTime.now()).toSeconds()
      );
    }

    log.info(
      "✅ Etapa 4 - Updates de SalesSummary concluídos para {}. trigger={}, atualizados={}, duração={}s",
      label,
      trigger,
      totalUpdated,
      Duration.between(startedAt, OffsetDateTime.now()).toSeconds()
    );
  }

  private void bulkUpdateManualGenerated(FinancialReconciliationTriggerType trigger, List<UUID> generatedSummaryIds) {
    if (generatedSummaryIds.isEmpty()) {
      return;
    }

    int totalUpdated = 0;
    int totalBatches = totalBatches(generatedSummaryIds.size(), UPDATE_BATCH_SIZE);
    int batchNumber = 0;

    for (List<UUID> batchIds : partition(generatedSummaryIds, UPDATE_BATCH_SIZE)) {
      batchNumber++;
      int updated = salesSummaryRepository.updateManualGeneratedByIds(batchIds, Boolean.TRUE);
      totalUpdated += updated;

      log.info(
        "🔄 Etapa 4 - Update manualGenerated batch {}/{} concluído. ids={}, atualizados={}, totalAtualizados={}",
        batchNumber,
        totalBatches,
        batchIds.size(),
        updated,
        totalUpdated
      );
    }

    log.info(
      "✅ Etapa 4 - SalesSummary manualGenerated atualizado. trigger={}, total={}",
      trigger,
      totalUpdated
    );
  }

  private void logPvMismatchDiagnosis(List<SalesSummaryEntity> summaries, FinancialReconciliationTriggerType trigger) {
    if (summaries.isEmpty()) return;

    Set<UUID> acquirerIds = summaries.stream()
      .filter(ss -> ss.getAcquirer() != null)
      .map(ss -> ss.getAcquirer().getId())
      .collect(Collectors.toSet());

    Set<Integer> rvNumbers = summaries.stream()
      .filter(ss -> ss.getRvNumber() != null)
      .map(SalesSummaryEntity::getRvNumber)
      .collect(Collectors.toSet());

    if (acquirerIds.isEmpty() || rvNumbers.isEmpty()) return;

    List<CreditOrderEntity> orphans = creditOrderRepository.findOrphanedByAcquirerIdsAndRvNumbers(acquirerIds, rvNumbers);

    if (orphans.isEmpty()) {
      log.info(
        "🔍 Etapa 4 - PV Mismatch: nenhuma CreditOrder órfã com acquirer+rvNumber correspondente aos {} SalesSummary pendentes. Os arquivos EEFI para esses RVs provavelmente não foram importados. trigger={}",
        summaries.size(), trigger
      );
      return;
    }

    // Para cada órfã que bate por acquirer+rvNumber, verifica se pvCentralizer difere do pvNumber do SalesSummary
    Map<String, Integer> summaryPvByKey = summaries.stream()
      .filter(ss -> ss.getAcquirer() != null && ss.getRvNumber() != null && ss.getPvNumber() != null)
      .collect(Collectors.toMap(
        ss -> ss.getAcquirer().getId() + ":" + ss.getRvNumber(),
        SalesSummaryEntity::getPvNumber,
        (a, b) -> a
      ));

    long pvMatch = 0;
    long pvMismatch = 0;
    Map<String, Long> mismatchPatterns = new java.util.LinkedHashMap<>();

    for (CreditOrderEntity co : orphans) {
      if (co.getAcquirer() == null || co.getRvNumber() == null) continue;
      String key = co.getAcquirer().getId() + ":" + co.getRvNumber();
      Integer summaryPv = summaryPvByKey.get(key);
      if (summaryPv == null) continue;

      if (summaryPv.equals(co.getPvCentralizer())) {
        pvMatch++;
      } else {
        pvMismatch++;
        String pattern = "summaryPv=" + summaryPv
          + " | coPvCentralizer=" + co.getPvCentralizer()
          + " | coOriginalPv=" + co.getOriginalPvNumber()
          + " | rv=" + co.getRvNumber();
        mismatchPatterns.merge(pattern, 1L, (a, b) -> a + b);
      }
    }

    if (pvMismatch > 0) {
      log.warn(
        "⚠️ Etapa 4 - PV Mismatch: {} CreditOrder(s) órfã(s) têm acquirer+rvNumber correspondente mas pvCentralizer diferente do pvNumber do SalesSummary. trigger={}",
        pvMismatch, trigger
      );
      mismatchPatterns.entrySet().stream()
        .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
        .limit(20)
        .forEach(e -> log.warn("   → count={} | {}", e.getValue(), e.getKey()));
    }

    if (pvMatch > 0) {
      log.warn(
        "⚠️ Etapa 4 - PV Match exacto mas ainda órfã: {} CreditOrder(s) com acquirer+rvNumber+pvCentralizer idêntico ao SalesSummary mas salesSummary=NULL. Pode ser bug na ingestão. trigger={}",
        pvMatch, trigger
      );
    }

    if (pvMismatch == 0 && pvMatch == 0) {
      log.info(
        "🔍 Etapa 4 - PV Mismatch: {} CreditOrder(s) órfã(s) encontradas por acquirer+rvNumber mas sem cruzamento direto com os SalesSummary pendentes (chaves incompatíveis). trigger={}",
        orphans.size(), trigger
      );
    }
  }

  private void logNotGeneratedDistribution(List<SalesSummaryEntity> summaries, FinancialReconciliationTriggerType trigger) {
    if (summaries.isEmpty()) return;

    Map<String, Long> distribution = summaries.stream().collect(Collectors.groupingBy(
      ss -> "modality=" + ss.getModality()
        + " | summaryType=" + upper(ss.getSummaryType())
        + " | recordType=" + upper(ss.getRecordType()),
      Collectors.counting()
    ));

    log.info(
      "🔍 Etapa 4 - Diagnóstico: {} SalesSummary sem CreditOrder e sem geração sintética. trigger={}, distribuição:",
      summaries.size(), trigger
    );
    distribution.entrySet().stream()
      .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
      .forEach(e -> log.info("   → count={} | {}", e.getValue(), e.getKey()));
  }

  /** Visibilidade de pacote (não private) para reuso em SalesSummaryPreImplantationReconciliationService#preview. */
  boolean shouldGenerateSyntheticCreditOrder(SalesSummaryEntity summary) {
    if (summary == null) return false;

    ModalityEnum modality = ModalityEnum.fromCode(summary.getModality());
    if (modality == ModalityEnum.CASH_DEBIT) return true;

    String summaryType = upper(summary.getSummaryType());
    String recordType = upper(summary.getRecordType());

    return summaryType.contains("ANTECIP")
      || summaryType.contains("ANTICIP")
      || recordType.contains("ANTECIP")
      || recordType.contains("ANTICIP")
      || summaryType.contains("DEBIT")
      || recordType.contains("DEBIT");
  }

  /** Visibilidade de pacote (não private) para permitir teste unitário direto sem contexto Spring. */
  CreditOrderEntity generateSyntheticCreditOrder(SalesSummaryEntity summary, BigDecimal debitAdjustments) {
    CreditOrderEntity order = new CreditOrderEntity();
    order.setSalesSummary(summary);
    order.setAcquirer(summary.getAcquirer());
    order.setFlag(summary.getFlag());
    order.setCompany(summary.getCompany());
    order.setBankingDomicile(summary.getBankingDomicile());
    order.setProcessedFile(summary.getProcessedFile());

    order.setRvNumber(summary.getRvNumber());
    order.setRvDate(summary.getRvDate());
    order.setReleaseDate(summary.getFirstInstallmentCreditDate() != null ? summary.getFirstInstallmentCreditDate() : summary.getRvDate());
    order.setCreditOrderDate(summary.getRvDate());
    order.setOriginalPvNumber(summary.getPvNumber());
    order.setPvCentralizer(summary.getPvNumber());
    order.setInstallmentNumber(1);
    order.setInstallmentTotal(1);
    order.setTransactionType(transactionTypeFromSummary(summary));
    order.setRecordType("MANUAL_GENERATED");
    order.setLaunchType("GENERATED_FROM_SALES_SUMMARY");

    order.setGrossRvValue(nvl(summary.getGrossValue()));
    order.setDiscountRateValue(nvl(summary.getDiscountValue()));
    // Desconta ajustes de débito (tarifa de POS, cancelamento de venda, etc. — qualquer
    // adjustmentType/motivo com debitType='D', ver AdjustmentRepository#sumDebitAdjustmentsBySalesSummaryIdIn)
    // do valor sintético, mesmo tratamento aplicado em CreditOrderManualService#buildCreditOrder pro
    // mesmo defeito. Sem isso a ordem saía com o valor cheio mesmo quando o resumo tinha ajustes que
    // já reduziam o valor real devido (confirmado com dados reais: RV 338015830, modalidade Débito,
    // liquidValue=52,29, ajustes de débito somando exatamente 52,29 — valor real devido é R$0,00,
    // mas a ordem sintética saía com R$52,29 via firstPositive(adjustedValue=0, liquidValue, gross)).
    BigDecimal baseValue = firstPositive(summary.getAdjustedValue(), summary.getLiquidValue(), summary.getGrossValue());
    order.setReleaseValue(baseValue.subtract(nvl(debitAdjustments)));

    order.setStatusPaymentBank(StatusPaymentBankEnum.PENDING);
    order.setCreditStatus(StatusPaymentBankEnum.PENDING.getCode());
    order.setSalesSummaryStatus(StatusReconciliationEnum.RECONCILED);
    order.setReconciliationStatus(StatusPaymentBankEnum.PENDING.getCode());

    return order;
  }

  /** Visibilidade de pacote (não private) para permitir teste unitário direto sem contexto Spring. */
  CreditOrderEntity generateSyntheticCreditOrder(AnticipationEntity anticipation) {
    CreditOrderEntity order = new CreditOrderEntity();
    order.setSalesSummary(anticipation.getSalesSummary());
    order.setAcquirer(anticipation.getAcquirer());
    order.setFlag(anticipation.getFlag());
    order.setCompany(anticipation.getCompany());
    order.setBankingDomicile(anticipation.getBankingDomicile());
    order.setProcessedFile(anticipation.getProcessedFile());

    order.setRvNumber(anticipation.getNumberRvCorresponding());
    order.setRvDate(anticipation.getDateRvCorresponding());
    order.setReleaseDate(anticipation.getReleaseDate());
    order.setCreditOrderDate(anticipation.getReleaseDate());
    order.setOriginalPvNumber(anticipation.getPvNumber());
    order.setPvCentralizer(anticipation.getPvNumber());
    order.setInstallmentNumber(anticipation.getInstallmentNumber());
    order.setInstallmentTotal(anticipation.getInstallmentNumberMax());
    order.setTransactionType(2);
    // record_type é varchar(20) — "GENERATED_FROM_ANTICIPATION" (28 chars) só cabe em
    // launch_type (varchar(30)). Ver MANUAL_GENERATED/GENERATED_FROM_SALES_SUMMARY acima.
    order.setRecordType("GEN_ANTICIPATION");
    order.setLaunchType("GENERATED_FROM_ANTICIPATION");

    order.setGrossRvValue(nvl(anticipation.getGrossValue()));
    order.setDiscountRateValue(nvl(anticipation.getDiscountRateValue()));
    order.setReleaseValue(nvl(anticipation.getReleaseValue()));

    order.setStatusPaymentBank(StatusPaymentBankEnum.PENDING);
    order.setCreditStatus(StatusPaymentBankEnum.PENDING.getCode());
    order.setSalesSummaryStatus(StatusReconciliationEnum.RECONCILED);
    order.setReconciliationStatus(StatusPaymentBankEnum.PENDING.getCode());

    return order;
  }

  private Integer transactionTypeFromSummary(SalesSummaryEntity summary) {
    ModalityEnum modality = ModalityEnum.fromCode(summary.getModality());
    if (modality == ModalityEnum.CASH_DEBIT) return 1;
    return 2;
  }

  private BigDecimal firstPositive(BigDecimal... values) {
    for (BigDecimal value : values) {
      if (value != null && value.compareTo(BigDecimal.ZERO) != 0) {
        return value;
      }
    }
    return BigDecimal.ZERO;
  }

  private BigDecimal nvl(BigDecimal value) {
    return value == null ? BigDecimal.ZERO : value;
  }

  private String upper(String value) {
    return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
  }

  private List<List<UUID>> partition(List<UUID> ids, int batchSize) {
    List<List<UUID>> batches = new ArrayList<>();
    for (int start = 0; start < ids.size(); start += batchSize) {
      batches.add(ids.subList(start, Math.min(start + batchSize, ids.size())));
    }
    return batches;
  }

  private int totalBatches(int total, int batchSize) {
    if (total <= 0) return 0;
    return (int) Math.ceil((double) total / batchSize);
  }

  private int safeInt(long value) {
    return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
  }

  private record GeneratedOrders(
    List<UUID> generatedSummaryIds,
    List<UUID> notGeneratedSummaryIds,
    int generatedOrders
  ) {
  }

  private static class Counter {
    private final FinancialReconciliationTriggerType trigger;
    private final OffsetDateTime startedAt;
    private int summariesAnalyzed;
    private int summariesReconciled;
    private int summariesPartiallyReconciled;
    private int summariesPending;
    private int summariesBlockedByPreviousStep;
    private int summariesWithoutCreditOrders;
    private int generatedCreditOrders;
    private int creditOrdersAnalyzed;
    private BigDecimal totalGrossValueReconciled = BigDecimal.ZERO;
    private BigDecimal totalGrossValuePending = BigDecimal.ZERO;

    private Counter(FinancialReconciliationTriggerType trigger, OffsetDateTime startedAt) {
      this.trigger = trigger;
      this.startedAt = startedAt;
    }

    private SalesSummaryCreditOrderReconciliationResult toResult(OffsetDateTime finishedAt) {
      return SalesSummaryCreditOrderReconciliationResult.builder()
        .trigger(trigger)
        .summariesAnalyzed(summariesAnalyzed)
        .summariesReconciled(summariesReconciled)
        .summariesPartiallyReconciled(summariesPartiallyReconciled)
        .summariesPending(summariesPending)
        .summariesBlockedByPreviousStep(summariesBlockedByPreviousStep)
        .summariesWithoutCreditOrders(summariesWithoutCreditOrders)
        .generatedCreditOrders(generatedCreditOrders)
        .creditOrdersAnalyzed(creditOrdersAnalyzed)
        .totalGrossValueReconciled(totalGrossValueReconciled)
        .totalGrossValuePending(totalGrossValuePending)
        .startedAt(startedAt)
        .finishedAt(finishedAt)
        .build();
    }
  }
}