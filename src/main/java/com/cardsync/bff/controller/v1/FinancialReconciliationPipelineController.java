package com.cardsync.bff.controller.v1;

import com.cardsync.bff.controller.v1.representation.model.conciliation.ReconciliationExecutionLogResponse;
import com.cardsync.core.reconciliation.BankReconciliationResult;
import com.cardsync.core.reconciliation.BankReconciliationService;
import com.cardsync.core.reconciliation.pipeline.FinancialReconciliationPipelineResult;
import com.cardsync.core.reconciliation.pipeline.FinancialReconciliationPipelineService;
import com.cardsync.core.reconciliation.pipeline.ReconciliationExecutionLogService;
import com.cardsync.core.reconciliation.summary.AcquirerSaleSummaryReconciliationResult;
import com.cardsync.core.reconciliation.summary.AcquirerSaleSummaryReconciliationService;
import com.cardsync.core.reconciliation.summary.SalesSummaryCreditOrderReconciliationResult;
import com.cardsync.core.reconciliation.summary.SalesSummaryCreditOrderReconciliationService;
import com.cardsync.core.security.CheckSecurity;
import com.cardsync.domain.model.enums.FinancialReconciliationTriggerType;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/bff/v1/conciliation/financial-pipeline")
public class FinancialReconciliationPipelineController {

  private final FinancialReconciliationPipelineService financialReconciliationPipelineService;
  private final ReconciliationExecutionLogService reconciliationExecutionLogService;
  private final AcquirerSaleSummaryReconciliationService acquirerSaleSummaryReconciliationService;
  private final SalesSummaryCreditOrderReconciliationService salesSummaryCreditOrderReconciliationService;
  private final BankReconciliationService bankReconciliationService;

  @GetMapping("/history")
  @CheckSecurity.Reconciliation.FinancialReconciliationPipeline.CanConsult
  public List<ReconciliationExecutionLogResponse> getHistory(
      @RequestParam(defaultValue = "20") int limit
  ) {
    return reconciliationExecutionLogService.findRecent(limit);
  }

  @PostMapping("/run")
  @CheckSecurity.Reconciliation.FinancialReconciliationPipeline.CanProcess
  public FinancialReconciliationPipelineResult runFullPipeline() {
    return financialReconciliationPipelineService.run(FinancialReconciliationTriggerType.MANUAL);
  }

  @PostMapping("/acquirer-sale-summary/reconcile")
  @CheckSecurity.Reconciliation.FinancialReconciliationPipeline.CanProcess
  public AcquirerSaleSummaryReconciliationResult reconcileAcquirerSaleSummary() {
    return acquirerSaleSummaryReconciliationService.reconcilePending(FinancialReconciliationTriggerType.MANUAL);
  }

  @PostMapping("/sales-summary-credit-order/reconcile")
  @CheckSecurity.Reconciliation.FinancialReconciliationPipeline.CanProcess
  public SalesSummaryCreditOrderReconciliationResult reconcileSalesSummaryCreditOrder() {
    return salesSummaryCreditOrderReconciliationService.reconcilePending(FinancialReconciliationTriggerType.MANUAL);
  }

  @PostMapping("/credit-order-bank-release/reconcile")
  @CheckSecurity.Reconciliation.FinancialReconciliationPipeline.CanProcess
  public BankReconciliationResult reconcileCreditOrderBankRelease() {
    return bankReconciliationService.reconcilePending();
  }
}
