package com.cardsync.core.reconciliation.summary;

import com.cardsync.core.file.config.FileProcessingProperties;
import com.cardsync.domain.model.CreditOrderEntity;
import com.cardsync.domain.model.SalesSummaryEntity;
import com.cardsync.domain.model.enums.*;
import com.cardsync.domain.repository.CreditOrderRepository;
import com.cardsync.domain.repository.SalesSummaryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SalesSummaryCreditOrderReconciliationService {

  private static final int UPDATE_BATCH_SIZE = 1_000;
  private static final int GENERATION_BATCH_SIZE = 1_000;

  private static final int ORDER_SUMMARY_PENDING = StatusReconciliationEnum.PENDING.getCode();
  private static final int ORDER_SUMMARY_RECONCILED = StatusReconciliationEnum.RECONCILED.getCode();

  /**
   * Etapa 5 - Resumo x ordem de pagamento.

   * Regra de dependência: somente resumos cuja conciliação Venda ADQ x Resumo (etapa 4)
   * ficou TOTALMENTE conciliada participam desta etapa. O campo {@code transactionsStatus}
   * é gravado pela etapa 4 usando {@link StatusReconciliationEnum}; portanto a elegibilidade
   * aqui deve usar o MESMO enum. Resumos parcialmente conciliados NÃO entram (ficam para o
   * próximo ciclo, quando — e se — forem totalmente conciliados).
   */
  private static final List<Integer> ELIGIBLE_TRANSACTION_SUMMARY_STATUSES = List.of(
    StatusReconciliationEnum.RECONCILED.getCode()
  );

  private static final List<Integer> PENDING_SUMMARY_CREDIT_ORDER_STATUSES = List.of(
    StatusReconciliationEnum.PENDING.getCode(),
    StatusReconciliationEnum.PARTIALLY_RECONCILED.getCode()
  );

  private final FileProcessingProperties properties;
  private final SalesSummaryRepository salesSummaryRepository;
  private final CreditOrderRepository creditOrderRepository;

  @Transactional
  public SalesSummaryCreditOrderReconciliationResult reconcilePending(FinancialReconciliationTriggerType trigger) {
    OffsetDateTime startedAt = OffsetDateTime.now();

    boolean reprocess = properties.getReconciliation().isReprocessSalesSummaryCreditOrder();

    log.info(
      "📌 Etapa 4 - Resumo x ordem iniciada. trigger={}, eligibleTransactionStatuses={}, pendingCreditOrderStatuses={}, updateBatchSize={}, generationBatchSize={}, reprocess={}",
      trigger,
      ELIGIBLE_TRANSACTION_SUMMARY_STATUSES,
      PENDING_SUMMARY_CREDIT_ORDER_STATUSES,
      UPDATE_BATCH_SIZE,
      GENERATION_BATCH_SIZE,
      reprocess
    );

    OffsetDateTime queryStartedAt = OffsetDateTime.now();

    List<SalesSummaryCreditOrderStats> stats = salesSummaryRepository.findStatsForSalesSummaryCreditOrderReconciliation(
      reprocess,
      ELIGIBLE_TRANSACTION_SUMMARY_STATUSES,
      PENDING_SUMMARY_CREDIT_ORDER_STATUSES
    );

    log.info(
      "🔎 Etapa 4 - Consulta agregada concluída. trigger={}, summariesCandidatos={}, duraçãoConsulta={}s",
      trigger,
      stats.size(),
      Duration.between(queryStartedAt, OffsetDateTime.now()).toSeconds()
    );

    Counter counter = new Counter(trigger, startedAt);
    counter.summariesAnalyzed = stats.size();

    List<UUID> summariesWithOrders = new ArrayList<>();
    List<UUID> summariesWithoutOrders = new ArrayList<>();
    long existingCreditOrders = 0L;

    for (SalesSummaryCreditOrderStats row : stats) {
      if (row.hasCreditOrders()) {
        summariesWithOrders.add(row.getSalesSummaryId());
        existingCreditOrders += row.creditOrdersCountSafe();
      } else {
        summariesWithoutOrders.add(row.getSalesSummaryId());
      }
    }

    counter.creditOrdersAnalyzed = safeInt(existingCreditOrders);

    log.info(
      "🧮 Etapa 4 - Classificação inicial concluída. trigger={}, summaries={}, comOrdens={}, semOrdens={}, ordensExistentes={}",
      trigger,
      stats.size(),
      summariesWithOrders.size(),
      summariesWithoutOrders.size(),
      existingCreditOrders
    );

    GeneratedOrders generated = generateSyntheticOrders(trigger, summariesWithoutOrders);

    counter.generatedCreditOrders = generated.generatedOrders();
    counter.creditOrdersAnalyzed += generated.generatedOrders();
    counter.summariesWithoutCreditOrders = generated.notGeneratedSummaryIds().size();
    counter.summariesPending = generated.notGeneratedSummaryIds().size();
    counter.summariesReconciled = summariesWithOrders.size() + generated.generatedSummaryIds().size();
    counter.summariesPartiallyReconciled = 0;
    counter.summariesBlockedByPreviousStep = 0;

    List<UUID> reconciledSummaryIds = new ArrayList<>(summariesWithOrders.size() + generated.generatedSummaryIds().size());
    reconciledSummaryIds.addAll(summariesWithOrders);
    reconciledSummaryIds.addAll(generated.generatedSummaryIds());

    bulkUpdateExistingCreditOrders(trigger, summariesWithOrders);
    bulkUpdateSalesSummaryStatuses(trigger, "conciliado", reconciledSummaryIds, ORDER_SUMMARY_RECONCILED);
    bulkUpdateSalesSummaryStatuses(trigger, "pendente/sem ordem", generated.notGeneratedSummaryIds(), ORDER_SUMMARY_PENDING);
    bulkUpdateManualGenerated(trigger, generated.generatedSummaryIds());

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

    int batchNumber = 0;
    for (List<UUID> batchIds : partition(summariesWithoutOrders, GENERATION_BATCH_SIZE)) {
      batchNumber++;
      OffsetDateTime batchStartedAt = OffsetDateTime.now();

      List<SalesSummaryEntity> summaries = salesSummaryRepository.findBatchForSalesSummaryCreditOrderReconciliation(batchIds);
      List<CreditOrderEntity> ordersToGenerate = new ArrayList<>();

      for (SalesSummaryEntity summary : summaries) {
        if (shouldGenerateSyntheticCreditOrder(summary)) {
          ordersToGenerate.add(generateSyntheticCreditOrder(summary));
          generatedSummaryIds.add(summary.getId());
        } else {
          notGeneratedSummaryIds.add(summary.getId());
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

    return new GeneratedOrders(generatedSummaryIds, notGeneratedSummaryIds, generatedOrders);
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

  private boolean shouldGenerateSyntheticCreditOrder(SalesSummaryEntity summary) {
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

  private CreditOrderEntity generateSyntheticCreditOrder(SalesSummaryEntity summary) {
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
    order.setReleaseValue(firstPositive(summary.getAdjustedValue(), summary.getLiquidValue(), summary.getGrossValue()));

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
        .startedAt(startedAt)
        .finishedAt(finishedAt)
        .build();
    }
  }
}