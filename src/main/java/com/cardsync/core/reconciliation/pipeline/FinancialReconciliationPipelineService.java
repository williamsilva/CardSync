package com.cardsync.core.reconciliation.pipeline;

import com.cardsync.bff.controller.v1.representation.model.conciliation.ReconcileErpAcquirerFeesResultModel;
import com.cardsync.bff.controller.v1.representation.model.conciliation.ReconcileErpAcquirerResultModel;
import com.cardsync.core.conciliation.analysis.ConciliationAnalysisService;
import com.cardsync.core.conciliation.analysis.ConciliationManualSwapReconciliationService;
import com.cardsync.core.reconciliation.BankReconciliationResult;
import com.cardsync.core.reconciliation.BankReconciliationTriggerType;
import com.cardsync.core.reconciliation.BankReconciliationService;
import com.cardsync.core.reconciliation.cancellation.AcquirerSaleCancellationResult;
import com.cardsync.core.reconciliation.cancellation.AcquirerSaleCancellationService;
import com.cardsync.core.reconciliation.summary.AcquirerSaleSummaryReconciliationResult;
import com.cardsync.core.reconciliation.summary.AcquirerSaleSummaryReconciliationService;
import com.cardsync.core.reconciliation.summary.CreditOrderOrphanLinkingService;
import com.cardsync.core.reconciliation.summary.SalesSummaryCreditOrderReconciliationResult;
import com.cardsync.core.reconciliation.summary.SalesSummaryCreditOrderReconciliationService;
import com.cardsync.core.reconciliation.summary.SalesSummaryTransactionReconciliationResult;
import com.cardsync.core.reconciliation.summary.SalesSummaryTransactionReconciliationService;
import com.cardsync.domain.model.enums.FinancialReconciliationTriggerType;
import com.cardsync.domain.model.enums.ReconciliationPipelineStepEnum;
import com.cardsync.domain.model.enums.ReconciliationPipelineStepStatusEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class FinancialReconciliationPipelineService {

  private final AtomicBoolean manualRunning = new AtomicBoolean(false);

  private final BankReconciliationService bankReconciliationService;
  private final ConciliationAnalysisService conciliationAnalysisService;
  private final AcquirerSaleCancellationService acquirerSaleCancellationService;
  private final CreditOrderOrphanLinkingService creditOrderOrphanLinkingService;
  private final AcquirerSaleSummaryReconciliationService acquirerSaleSummaryReconciliationService;
  private final ConciliationManualSwapReconciliationService conciliationManualSwapReconciliationService;
  private final SalesSummaryCreditOrderReconciliationService salesSummaryCreditOrderReconciliationService;
  private final SalesSummaryTransactionReconciliationService salesSummaryTransactionReconciliationService;

  public boolean isManualRunning() {
    return manualRunning.get();
  }

  /**
   * Não manter @Transactional aqui.

   * A esteira inteira pode processar muitos registros. Uma transação única envolvendo todas as etapas
   * segura locks por tempo demais, aumenta memória do persistence context e dificulta o diagnóstico.
   * Cada serviço interno controla sua própria transação.
   */
  public FinancialReconciliationPipelineResult run(FinancialReconciliationTriggerType trigger) {
    boolean isManual = trigger == FinancialReconciliationTriggerType.MANUAL;

    if (isManual && !manualRunning.compareAndSet(false, true)) {
      throw new IllegalStateException("Conciliação financeira manual já está em execução.");
    }

    OffsetDateTime startedAt = OffsetDateTime.now();

    try {
      log.info("▶ ESTEIRA DE CONCILIAÇÃO FINANCEIRA iniciada. trigger={}, startedAt={}", trigger, startedAt);

      FinancialReconciliationPipelineResult result = FinancialReconciliationPipelineResult.builder()
        .trigger(trigger)
        .startedAt(startedAt)
        .build();

      result.addStep(executePipelineStep("1. ADQ x ERP", () -> runErpAcquirer(trigger)));
      result.addStep(executePipelineStep("2. Resumo de vendas x TransactionAcq", () -> runSalesSummaryTransactions(trigger)));
      result.addStep(executePipelineStep("3. Cancelamentos informados pela adquirente", () -> runAcquirerSaleCancellations(trigger)));
      result.addStep(executePipelineStep("4. Ajustes/taxas ERP x Adquirente", () -> runSaleAdjustments(trigger)));
      result.addStep(executePipelineStep("5. Venda ADQ x resumo de vendas", () -> runAcquirerSaleSummary(trigger)));
      result.addStep(executePipelineStep("6. Resumo de vendas x ordem de pagamento", () -> runSalesSummaryCreditOrder(trigger)));
      result.addStep(executePipelineStep("7. Ordem de pagamento x lançamento bancário", () -> runCreditOrderBankRelease(trigger)));

      OffsetDateTime finishedAt = OffsetDateTime.now();
      result.setFinishedAt(finishedAt);

      log.info(
        "📘 ESTEIRA DE CONCILIAÇÃO FINANCEIRA FINALIZADA: trigger={}, etapas={}, duraçãoTotal={}s",
        trigger,
        result.getSteps().size(),
        Duration.between(startedAt, finishedAt).toSeconds()
      );

      return result;
    } finally {
      if (isManual) {
        manualRunning.set(false);
      }
    }
  }

  private FinancialReconciliationStepResult executePipelineStep(
    String name,
    Supplier<FinancialReconciliationStepResult> action
  ) {
    OffsetDateTime startedAt = OffsetDateTime.now();

    log.info("▶ Iniciando etapa da esteira financeira: {} às {}", name, startedAt);

    try {
      FinancialReconciliationStepResult result = action.get();
      OffsetDateTime finishedAt = OffsetDateTime.now();

      log.info(
        "✅ Etapa da esteira financeira finalizada: {} às {}. duração={}s, analyzed={}, reconciled={}, partial={}, pending={}, blocked={}, updated={}, divergent={}, withoutMatch={}, generated={}",
        name,
        finishedAt,
        Duration.between(startedAt, finishedAt).toSeconds(),
        result.getAnalyzed(),
        result.getReconciled(),
        result.getPartiallyReconciled(),
        result.getPending(),
        result.getBlocked(),
        result.getUpdated(),
        result.getDivergent(),
        result.getWithoutMatch(),
        result.getGenerated()
      );

      return result;
    } catch (Exception ex) {
      OffsetDateTime failedAt = OffsetDateTime.now();

      log.error(
        "❌ Etapa da esteira financeira falhou: {} às {}. duraçãoAtéFalha={}s, erro={}",
        name,
        failedAt,
        Duration.between(startedAt, failedAt).toSeconds(),
        ex.getMessage(),
        ex
      );

      throw ex;
    }
  }

  private FinancialReconciliationStepResult runErpAcquirer(FinancialReconciliationTriggerType trigger) {
    OffsetDateTime startedAt = OffsetDateTime.now();
    ReconcileErpAcquirerResultModel erpAcq = conciliationAnalysisService
      .reconcileRedeErpWithAcquirer(trigger.name());

    // Após a conciliação principal, trata as vendas MANUAIS que sobraram pendentes
    // por terem NSU e autorização invertidos.
    ReconcileErpAcquirerResultModel manualSwap = conciliationManualSwapReconciliationService
      .reconcileRedeManualSwapped(trigger.name());

    return FinancialReconciliationStepResult.builder()
      .step(ReconciliationPipelineStepEnum.ERP_ACQUIRER)
      .status(ReconciliationPipelineStepStatusEnum.COMPLETED)
      .message("Etapa 1 concluída (inclui reprocesso de manuais com NSU/autorização invertidos). Somente vendas conciliadas aqui ficam elegíveis para ajustes/taxas.")
      .analyzed(erpAcq.analyzed() + manualSwap.analyzed())
      .reconciled(erpAcq.matched() + manualSwap.matched())
      .updated(erpAcq.updated() + manualSwap.updated())
      .withoutMatch(manualSwap.notMatched())
      .divergent(
        erpAcq.valueDivergences() + erpAcq.acquirerDivergences() + erpAcq.ambiguousMatches()
          + manualSwap.valueDivergences() + manualSwap.acquirerDivergences() + manualSwap.ambiguousMatches()
      )
      .startedAt(startedAt)
      .finishedAt(OffsetDateTime.now())
      .build();
  }

  private FinancialReconciliationStepResult runSalesSummaryTransactions(FinancialReconciliationTriggerType trigger) {
    OffsetDateTime startedAt = OffsetDateTime.now();

    SalesSummaryTransactionReconciliationResult r = salesSummaryTransactionReconciliationService
      .reconcile(trigger);

    return FinancialReconciliationStepResult.builder()
      .step(ReconciliationPipelineStepEnum.SALES_SUMMARY_TRANSACTION)
      .status(ReconciliationPipelineStepStatusEnum.COMPLETED)
      .message("Etapa 2 concluída. Resumos marcados conforme estado das transações ADQ (sem gate de taxa). Resumos sem transações marcados como conciliados.")
      .analyzed(r.getSummariesAnalyzed())
      .reconciled(r.getSummariesReconciled())
      .partiallyReconciled(r.getSummariesPartiallyReconciled())
      .pending(r.getSummariesPending())
      .startedAt(startedAt)
      .finishedAt(OffsetDateTime.now())
      .build();
  }

  private FinancialReconciliationStepResult runAcquirerSaleCancellations(FinancialReconciliationTriggerType trigger) {
    OffsetDateTime startedAt = OffsetDateTime.now();

    AcquirerSaleCancellationResult cancellations = acquirerSaleCancellationService.reconcilePending(trigger);

    return FinancialReconciliationStepResult.builder()
      .step(ReconciliationPipelineStepEnum.ACQUIRER_SALE_CANCELLATION)
      .status(ReconciliationPipelineStepStatusEnum.COMPLETED)
      .message("Etapa 2 concluída. Cancelamentos totais informados pela adquirente foram propagados para ADQ/ERP antes das taxas e resumos.")
      .analyzed(cancellations.getAdjustmentsAnalyzed())
      .reconciled(cancellations.getFullCancellationsIdentified())
      .updated(cancellations.getAcquirerSalesCanceled() + cancellations.getErpSalesCanceled())
      .blocked(cancellations.getSkippedWithoutTransaction())
      .pending(cancellations.getSkippedPartialCancellations())
      .startedAt(startedAt)
      .finishedAt(OffsetDateTime.now())
      .build();
  }

  private FinancialReconciliationStepResult runSaleAdjustments(FinancialReconciliationTriggerType trigger) {
    OffsetDateTime startedAt = OffsetDateTime.now();

    ReconcileErpAcquirerFeesResultModel fees = conciliationAnalysisService
      .reconcileRedeErpAcquirerFees(trigger.name());

    return FinancialReconciliationStepResult.builder()
      .step(ReconciliationPipelineStepEnum.ACQUIRER_SALE_ADJUSTMENTS)
      .status(ReconciliationPipelineStepStatusEnum.COMPLETED)
      .message("Etapa 3 concluída. Taxas OK ou contrato ausente normalizado avançam; divergência de taxa bloqueia próximas etapas.")
      .analyzed(fees.analyzed())
      .reconciled(fees.okRates() + fees.missingValidContracts())
      .updated(fees.updatedErpSales())
      .divergent(fees.divergentRates())
      .blocked(fees.skippedWithoutAcquirer())
      .startedAt(startedAt)
      .finishedAt(OffsetDateTime.now())
      .build();
  }

  private FinancialReconciliationStepResult runAcquirerSaleSummary(FinancialReconciliationTriggerType trigger) {
    OffsetDateTime startedAt = OffsetDateTime.now();

    AcquirerSaleSummaryReconciliationResult r = acquirerSaleSummaryReconciliationService
      .reconcilePending(trigger);

    return FinancialReconciliationStepResult.builder()
      .step(ReconciliationPipelineStepEnum.ACQUIRER_SALE_SUMMARY)
      .status(ReconciliationPipelineStepStatusEnum.COMPLETED)
      .message("Etapa 4 concluída. Resumo só concilia quando as vendas ADQ vinculadas estão elegíveis das etapas 1 e 2.")
      .analyzed(r.getSummariesAnalyzed())
      .reconciled(r.getSummariesReconciled())
      .partiallyReconciled(r.getSummariesPartiallyReconciled())
      .pending(r.getSummariesPending())
      .blocked(r.getSummariesBlockedByPreviousStep())
      .startedAt(startedAt)
      .finishedAt(OffsetDateTime.now())
      .build();
  }

  private FinancialReconciliationStepResult runSalesSummaryCreditOrder(FinancialReconciliationTriggerType trigger) {
    OffsetDateTime startedAt = OffsetDateTime.now();

    int linked = creditOrderOrphanLinkingService.linkOrphanedCreditOrders();
    if (linked > 0) {
      log.info("🔗 Etapa 6 - Pré-vinculação concluída: {} CreditOrder(s) órfã(s) vinculada(s) antes da conciliação.", linked);
    }

    SalesSummaryCreditOrderReconciliationResult r = salesSummaryCreditOrderReconciliationService
      .reconcilePending(trigger);

    return FinancialReconciliationStepResult.builder()
      .step(ReconciliationPipelineStepEnum.SALES_SUMMARY_CREDIT_ORDER)
      .status(ReconciliationPipelineStepStatusEnum.COMPLETED)
      .message("Etapa 5 concluída. Débito/antecipação sem ordem pode gerar ordem sintética para análise.")
      .analyzed(r.getSummariesAnalyzed())
      .reconciled(r.getSummariesReconciled())
      .partiallyReconciled(r.getSummariesPartiallyReconciled())
      .pending(r.getSummariesPending())
      .blocked(r.getSummariesBlockedByPreviousStep())
      .generated(r.getGeneratedCreditOrders())
      .startedAt(startedAt)
      .finishedAt(OffsetDateTime.now())
      .build();
  }

  private FinancialReconciliationStepResult runCreditOrderBankRelease(FinancialReconciliationTriggerType trigger) {
    OffsetDateTime startedAt = OffsetDateTime.now();

    BankReconciliationTriggerType bankTrigger = trigger == FinancialReconciliationTriggerType.MANUAL
      ? BankReconciliationTriggerType.MANUAL
      : BankReconciliationTriggerType.SCHEDULER_BANK_RECONCILIATION;

    BankReconciliationResult r = bankReconciliationService.reconcilePending(bankTrigger);

    return FinancialReconciliationStepResult.builder()
      .step(ReconciliationPipelineStepEnum.CREDIT_ORDER_BANK_RELEASE)
      .status(ReconciliationPipelineStepStatusEnum.COMPLETED)
      .message("Etapa 6 concluída. Ordem só fica elegível quando o resumo já está conciliado com a ordem.")
      .analyzed(r.releasesAnalyzed())
      .reconciled(r.releasesReconciled())
      .withoutMatch(r.releasesWithoutMatch())
      .blocked(r.releasesSkippedMissingContext())
      .updated(r.transactionsUpdated())
      .startedAt(startedAt)
      .finishedAt(OffsetDateTime.now())
      .build();
  }
}