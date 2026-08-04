package com.cardsync.core.reconciliation.pipeline;

import com.cardsync.bff.controller.v1.representation.model.conciliation.ReconcileErpAcquirerFeesResultModel;
import com.cardsync.bff.controller.v1.representation.model.conciliation.ReconcileErpAcquirerResultModel;
import com.cardsync.core.conciliation.analysis.ConciliationAnalysisService;
import com.cardsync.core.conciliation.analysis.ConciliationManualSwapReconciliationService;
import com.cardsync.core.reconciliation.BankReconciliationResult;
import com.cardsync.core.reconciliation.BankReconciliationTriggerType;
import com.cardsync.core.reconciliation.BankReconciliationService;
import com.cardsync.bff.controller.v1.representation.model.conciliation.ErpCancellationReprocessResult;
import com.cardsync.core.reconciliation.cancellation.AcquirerSaleCancellationResult;
import com.cardsync.core.reconciliation.cancellation.AcquirerSaleCancellationService;
import com.cardsync.core.reconciliation.cancellation.ErpCancellationReprocessService;
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
import com.cardsync.core.conciliation.ReconciliationSettingsService;
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

  private final ReconciliationExecutionLogService reconciliationExecutionLogService;
  private final ReconciliationSettingsService reconciliationSettingsService;
  private final BankReconciliationService bankReconciliationService;
  private final ConciliationAnalysisService conciliationAnalysisService;
  private final AcquirerSaleCancellationService acquirerSaleCancellationService;
  private final ErpCancellationReprocessService erpCancellationReprocessService;
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

      // Etapas em try/catch próprio: cada etapa gerencia sua própria transação (comentário
      // da classe), então uma falha no meio já deixa etapas anteriores commitadas de verdade
      // no banco. Sem isso, uma exceção aqui propagava direto pro finally, pulando o save()
      // abaixo — o histórico de execução (usado pelo dashboard de conciliação) ficava com um
      // buraco silencioso: nem "sucesso parcial", nem "falhou na etapa N", nada persistido.
      try {
        result.addStep(reconciliationSettingsService.isEnabledErpAcquirer()
          ? executePipelineStep("1. ADQ x ERP", () -> runErpAcquirer(trigger))
          : skippedStep(ReconciliationPipelineStepEnum.ERP_ACQUIRER, "1. ADQ x ERP"));
        result.addStep(reconciliationSettingsService.isEnabledSalesSummaryTransactions()
          ? executePipelineStep("2. Resumo de vendas x TransactionAcq", () -> runSalesSummaryTransactions(trigger))
          : skippedStep(ReconciliationPipelineStepEnum.SALES_SUMMARY_TRANSACTION, "2. Resumo de vendas x TransactionAcq"));
        result.addStep(reconciliationSettingsService.isEnabledAcquirerSaleCancellations()
          ? executePipelineStep("3. Cancelamentos informados pela adquirente", () -> runAcquirerSaleCancellations(trigger))
          : skippedStep(ReconciliationPipelineStepEnum.ACQUIRER_SALE_CANCELLATION, "3. Cancelamentos informados pela adquirente"));
        result.addStep(reconciliationSettingsService.isEnabledErpAcquirerFees()
          ? executePipelineStep("4. Ajustes/taxas ERP x Adquirente", () -> runSaleAdjustments(trigger))
          : skippedStep(ReconciliationPipelineStepEnum.ACQUIRER_SALE_ADJUSTMENTS, "4. Ajustes/taxas ERP x Adquirente"));
        result.addStep(reconciliationSettingsService.isEnabledAcquirerSaleSummary()
          ? executePipelineStep("5. Venda ADQ x resumo de vendas", () -> runAcquirerSaleSummary(trigger))
          : skippedStep(ReconciliationPipelineStepEnum.ACQUIRER_SALE_SUMMARY, "5. Venda ADQ x resumo de vendas"));
        result.addStep(reconciliationSettingsService.isEnabledSalesSummaryCreditOrder()
          ? executePipelineStep("6. Resumo de vendas x ordem de pagamento", () -> runSalesSummaryCreditOrder(trigger))
          : skippedStep(ReconciliationPipelineStepEnum.SALES_SUMMARY_CREDIT_ORDER, "6. Resumo de vendas x ordem de pagamento"));
        result.addStep(reconciliationSettingsService.isEnabledBankAcquirer()
          ? executePipelineStep("7. Ordem de pagamento x lançamento bancário", () -> runCreditOrderBankRelease(trigger))
          : skippedStep(ReconciliationPipelineStepEnum.CREDIT_ORDER_BANK_RELEASE, "7. Ordem de pagamento x lançamento bancário"));
      } catch (Exception ex) {
        OffsetDateTime failedAt = OffsetDateTime.now();
        result.setFinishedAt(failedAt);

        log.error(
          "❌ ESTEIRA DE CONCILIAÇÃO FINANCEIRA FALHOU: trigger={}, etapasConcluidas={}, duraçãoAtéFalha={}s, erro={}",
          trigger,
          result.getSteps().size(),
          Duration.between(startedAt, failedAt).toSeconds(),
          ex.getMessage()
        );

        reconciliationExecutionLogService.save(result);
        throw ex;
      }

      OffsetDateTime finishedAt = OffsetDateTime.now();
      result.setFinishedAt(finishedAt);

      log.info(
        "📘 ESTEIRA DE CONCILIAÇÃO FINANCEIRA FINALIZADA: trigger={}, etapas={}, duraçãoTotal={}s",
        trigger,
        result.getSteps().size(),
        Duration.between(startedAt, finishedAt).toSeconds()
      );

      reconciliationExecutionLogService.save(result);
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

  private FinancialReconciliationStepResult skippedStep(ReconciliationPipelineStepEnum step, String name) {
    OffsetDateTime now = OffsetDateTime.now();
    log.info("⏭ Etapa da esteira ignorada (desabilitada nas configurações): {}", name);
    return FinancialReconciliationStepResult.builder()
      .step(step)
      .status(ReconciliationPipelineStepStatusEnum.SKIPPED)
      .message("conciliation.dashboard.history.stepMessage.SKIPPED")
      .startedAt(now)
      .finishedAt(now)
      .build();
  }

  private FinancialReconciliationStepResult runErpAcquirer(FinancialReconciliationTriggerType trigger) {
    OffsetDateTime startedAt = OffsetDateTime.now();
    ReconcileErpAcquirerResultModel erpAcq = conciliationAnalysisService
      .reconcileErpWithAcquirer(trigger.name());

    // Após a conciliação principal, trata as vendas MANUAIS que sobraram pendentes
    // por terem NSU e autorização invertidos.
    ReconcileErpAcquirerResultModel manualSwap = conciliationManualSwapReconciliationService
      .reconcileManualSwapped(trigger.name());

    return FinancialReconciliationStepResult.builder()
      .step(ReconciliationPipelineStepEnum.ERP_ACQUIRER)
      .status(ReconciliationPipelineStepStatusEnum.COMPLETED)
      .message("conciliation.dashboard.history.stepMessage.ERP_ACQUIRER")
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
      .message("conciliation.dashboard.history.stepMessage.SALES_SUMMARY_TRANSACTION")
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
    ErpCancellationReprocessResult erpReprocess = erpCancellationReprocessService.reprocessPendingAll();

    return FinancialReconciliationStepResult.builder()
      .step(ReconciliationPipelineStepEnum.ACQUIRER_SALE_CANCELLATION)
      .status(ReconciliationPipelineStepStatusEnum.COMPLETED)
      .message("conciliation.dashboard.history.stepMessage.ACQUIRER_SALE_CANCELLATION")
      .analyzed(cancellations.getAdjustmentsAnalyzed())
      .reconciled(cancellations.getFullCancellationsIdentified())
      .updated(cancellations.getAcquirerSalesCanceled() + cancellations.getErpSalesCanceled()
        + erpReprocess.erpSalesCancelled() + erpReprocess.erpInstallmentsCancelled())
      .blocked(cancellations.getSkippedWithoutTransaction())
      .pending(cancellations.getSkippedPartialCancellations())
      .startedAt(startedAt)
      .finishedAt(OffsetDateTime.now())
      .build();
  }

  private FinancialReconciliationStepResult runSaleAdjustments(FinancialReconciliationTriggerType trigger) {
    OffsetDateTime startedAt = OffsetDateTime.now();

    ReconcileErpAcquirerFeesResultModel fees = conciliationAnalysisService
      .reconcileErpAcquirerFees(trigger.name());

    return FinancialReconciliationStepResult.builder()
      .step(ReconciliationPipelineStepEnum.ACQUIRER_SALE_ADJUSTMENTS)
      .status(ReconciliationPipelineStepStatusEnum.COMPLETED)
      .message("conciliation.dashboard.history.stepMessage.ACQUIRER_SALE_ADJUSTMENTS")
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
      .message("conciliation.dashboard.history.stepMessage.ACQUIRER_SALE_SUMMARY")
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
      .message("conciliation.dashboard.history.stepMessage.SALES_SUMMARY_CREDIT_ORDER")
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
      .message("conciliation.dashboard.history.stepMessage.CREDIT_ORDER_BANK_RELEASE")
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