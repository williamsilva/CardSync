package com.cardsync.bff.controller.v1;

import com.cardsync.bff.controller.v1.representation.model.conciliation.*;
import com.cardsync.bff.controller.v1.representation.model.transactions.TransactionTotalsModel;
import com.cardsync.core.conciliation.analysis.ConciliationAnalysisService;
import com.cardsync.core.conciliation.analysis.ConciliationManualSwapReconciliationService;
import com.cardsync.core.conciliation.analysis.ErpAcquirerResolutionService;
import com.cardsync.core.reconciliation.BankReconciliationResult;
import com.cardsync.core.reconciliation.BankReconciliationService;
import com.cardsync.core.reconciliation.cancellation.ErpCancellationReprocessService;
import com.cardsync.core.reconciliation.summary.AcquirerSaleSummaryReconciliationResult;
import com.cardsync.core.reconciliation.summary.AcquirerSaleSummaryReconciliationService;
import com.cardsync.core.reconciliation.summary.CreditOrderOrphanLinkingService;
import com.cardsync.core.reconciliation.summary.SalesSummaryCreditOrderReconciliationResult;
import com.cardsync.core.reconciliation.summary.SalesSummaryCreditOrderReconciliationService;
import com.cardsync.core.reconciliation.summary.SalesSummaryTransactionReconciliationResult;
import com.cardsync.core.reconciliation.summary.SalesSummaryTransactionReconciliationService;
import com.cardsync.core.security.CheckSecurity;
import com.cardsync.domain.model.enums.FinancialReconciliationTriggerType;
import com.cardsync.domain.filter.ConciliationWaitingModelFilter;
import com.cardsync.domain.filter.query.ListQueryDto;
import com.cardsync.domain.filter.support.PageableMapper;
import com.cardsync.domain.service.ConciliationWaitingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.hateoas.PagedModel;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/bff/v1/conciliation-waiting")
public class ConciliationWaitingController {

  private final BankReconciliationService bankReconciliationService;
  private final ConciliationWaitingService conciliationWaitingService;
  private final ConciliationAnalysisService conciliationAnalysisService;
  private final ErpAcquirerResolutionService erpAcquirerResolutionService;
  private final ErpCancellationReprocessService erpCancellationReprocessService;
  private final AcquirerSaleSummaryReconciliationService acquirerSaleSummaryReconciliationService;
  private final CreditOrderOrphanLinkingService creditOrderOrphanLinkingService;
  private final ConciliationManualSwapReconciliationService conciliationManualSwapReconciliationService;
  private final SalesSummaryTransactionReconciliationService salesSummaryTransactionReconciliationService;
  private final SalesSummaryCreditOrderReconciliationService salesSummaryCreditOrderReconciliationService;

  @PostMapping("/missing-acquirer")
  @CheckSecurity.FileProcessing.CanRead
  public PagedModel<ConciliationWaitingModel> missingAcquirer(@RequestBody ListQueryDto<ConciliationWaitingModelFilter> body) {
    var pageable = PageableMapper.toPageable(body.page(), body.size(), body.sort());

    Page<ConciliationWaitingModel> page = conciliationWaitingService.missingAcquirer(pageable, body);

    return PagedModel.of(
      page.getContent(),
      new PagedModel.PageMetadata(
        page.getSize(),
        page.getNumber(),
        page.getTotalElements(),
        page.getTotalPages()
      )
    );
  }

  @PostMapping("/missing-erp")
  @CheckSecurity.FileProcessing.CanRead
  public PagedModel<ConciliationWaitingModel> missingErp(@RequestBody ListQueryDto<ConciliationWaitingModelFilter> body) {
    var pageable = PageableMapper.toPageable(body.page(), body.size(), body.sort());

    Page<ConciliationWaitingModel> page = conciliationWaitingService.missingErp(pageable, body);

    return PagedModel.of(
      page.getContent(),
      new PagedModel.PageMetadata(
        page.getSize(),
        page.getNumber(),
        page.getTotalElements(),
        page.getTotalPages()
      )
    );
  }

  @PostMapping("/other-divergences")
  @CheckSecurity.FileProcessing.CanRead
  public PagedModel<ConciliationWaitingModel> otherDivergences(@RequestBody ListQueryDto<ConciliationWaitingModelFilter> body) {
    var pageable = PageableMapper.toPageable(body.page(), body.size(), body.sort());

    Page<ConciliationWaitingModel> page = conciliationWaitingService.otherDivergences(pageable, body);

    return PagedModel.of(
      page.getContent(),
      new PagedModel.PageMetadata(
        page.getSize(),
        page.getNumber(),
        page.getTotalElements(),
        page.getTotalPages()
      )
    );
  }

  @CheckSecurity.FileProcessing.CanRead
  @PostMapping("/missing-acquirer-totals")
  public TransactionTotalsModel missingAcquirerTotals(@RequestBody ListQueryDto<ConciliationWaitingModelFilter> body) {
    return conciliationWaitingService.missingAcquirerTotals(body);
  }

  @CheckSecurity.FileProcessing.CanRead
  @PostMapping("/missing-erp-totals")
  public TransactionTotalsModel missingErpTotals(@RequestBody ListQueryDto<ConciliationWaitingModelFilter> body) {
    return conciliationWaitingService.missingErpTotals(body);
  }

  @CheckSecurity.FileProcessing.CanRead
  @PostMapping("/other-divergences-totals")
  public TransactionTotalsModel otherDivergencesTotals(@RequestBody ListQueryDto<ConciliationWaitingModelFilter> body) {
    return conciliationWaitingService.otherDivergencesTotals(body);
  }

  @CheckSecurity.FileProcessing.CanProcess
  @PostMapping("/acquirer/{acquirerId}/create-erp")
  public ErpAcquirerResolutionResultModel createErpFromAcquirer(@PathVariable UUID acquirerId) {
    return conciliationWaitingService.createErpFromAcquirer(acquirerId);
  }

  @CheckSecurity.FileProcessing.CanProcess
  @PostMapping("/acquirer/create-erp-batch")
  public ErpAcquirerBatchResolutionResultModel createErpFromAcquirerBatch(
    @RequestBody ErpAcquirerBatchRequestModel request
  ) {
    return conciliationWaitingService.createErpFromAcquirerBatch(request.transactionIds());
  }

  @PatchMapping("/erp/{erpId}/update-identity")
  @CheckSecurity.FileProcessing.CanProcess
  public ErpAcquirerResolutionResultModel updateErpIdentity(
    @PathVariable UUID erpId,
    @RequestBody ErpUpdateIdentityRequest request
  ) {
    return conciliationWaitingService.updateErpIdentity(erpId, request);
  }

  @PostMapping("/erp/{erpId}/mark-deleted")
  @CheckSecurity.FileProcessing.CanProcess
  public ErpAcquirerResolutionResultModel markErpAsDeletedMissingAcquirer(
    @PathVariable UUID erpId,
    @RequestBody ErpMarkDeletedRequestModel request
  ) {
    return conciliationWaitingService.markErpAsDeletedMissingAcquirer(erpId, request);
  }

  @PostMapping("/erp/mark-deleted-batch")
  @CheckSecurity.FileProcessing.CanProcess
  public ErpAcquirerBatchResolutionResultModel markErpAsDeletedMissingAcquirerBatch(
    @RequestBody ErpAcquirerBatchRequestModel request
  ) {
    return conciliationWaitingService.markErpAsDeletedMissingAcquirerBatch(request);
  }

  @GetMapping("/compare")
  @CheckSecurity.FileProcessing.CanRead
  public ErpAcquirerComparisonModel compareErpAcquirer(
    @RequestParam UUID erpTransactionId,
    @RequestParam UUID acquirerTransactionId
  ) {
    return erpAcquirerResolutionService.compare(erpTransactionId, acquirerTransactionId);
  }

  @PostMapping("/reconcile")
  @CheckSecurity.FileProcessing.CanProcess
  public ReconcileErpAcquirerResultModel reconcileErpVsAcquirer() {
    ReconcileErpAcquirerResultModel result = conciliationAnalysisService.reconcileRedeErpWithAcquirer();
    salesSummaryTransactionReconciliationService.reconcile(FinancialReconciliationTriggerType.MANUAL);
    return result;
  }

  @PostMapping("/reconcile-manual-swapped")
  @CheckSecurity.FileProcessing.CanProcess
  public ReconcileErpAcquirerResultModel reconcileManualSwapped() {
    return conciliationManualSwapReconciliationService.reconcileRedeManualSwapped();
  }

  @PostMapping("/reconcile-fees")
  @CheckSecurity.FileProcessing.CanProcess
  public ReconcileErpAcquirerFeesResultModel reconcileErpAcquirerFees() {
    return conciliationAnalysisService.reconcileRedeErpAcquirerFees();
  }

  @PostMapping("/reconcile-manually")
  @CheckSecurity.FileProcessing.CanProcess
  public ErpAcquirerResolutionResultModel reconcileManually(
    @RequestParam UUID erpTransactionId,
    @RequestParam UUID acquirerTransactionId,
    @RequestParam ErpAcquirerTruthSource truthSource
  ) {
    return erpAcquirerResolutionService.reconcileManually(
      erpTransactionId,
      acquirerTransactionId,
      truthSource
    );
  }

  @PostMapping("/reprocess-erp-cancellations")
  @CheckSecurity.FileProcessing.CanProcess
  public ErpCancellationReprocessResult reprocessErpCancellations(
    @Valid @RequestBody ErpCancellationReprocessRequest request
  ) {
    return erpCancellationReprocessService.reprocess(request.year(), request.month());
  }

  @PostMapping("/reconcile-bank")
  @CheckSecurity.FileProcessing.CanProcess
  public BankReconciliationResult reconcileBank() {
    return bankReconciliationService.reconcilePending();
  }

  // ignoreLookback=true: backfill único, ignora a janela de lookback da esteira normal —
  // usado para corrigir/vincular resumos e ordens antigas que já saíram dessa janela.
  // Também roda a pré-vinculação de CreditOrder órfãs antes da conciliação, igual à
  // esteira automática (FinancialReconciliationPipelineService.runSalesSummaryCreditOrder).
  @PostMapping("/reconcile-sales-summary-credit-order")
  @CheckSecurity.FileProcessing.CanProcess
  public SalesSummaryCreditOrderReconciliationResult reconcileSalesSummaryCreditOrder(
    @RequestParam(defaultValue = "false") boolean ignoreLookback
  ) {
    creditOrderOrphanLinkingService.linkOrphanedCreditOrders(ignoreLookback);
    return salesSummaryCreditOrderReconciliationService.reconcilePending(FinancialReconciliationTriggerType.MANUAL, ignoreLookback);
  }

  // Etapa 2 — Resumo de Vendas x TransactionAcq (endpoint independente)
  // ignoreLookback=true: backfill único, ignora a janela de lookback da esteira normal —
  // usado para corrigir resumos antigos cujo transactionsStatus ficou desatualizado por
  // uma ação manual (reconciliação manual, criação de ERP a partir da adquirente) feita
  // antes do recálculo pontual existir.
  @PostMapping("/reconcile-sales-summary-transactions")
  @CheckSecurity.FileProcessing.CanProcess
  public SalesSummaryTransactionReconciliationResult reconcileSalesSummaryTransactions(
    @RequestParam(defaultValue = "false") boolean ignoreLookback
  ) {
    return salesSummaryTransactionReconciliationService.reconcile(FinancialReconciliationTriggerType.MANUAL, ignoreLookback);
  }

  // Etapa 5 — Venda ADQ x Resumo de Vendas
  // ignoreLookback=true: backfill único, ignora a janela de lookback da esteira normal.
  @PostMapping("/reconcile-acquirer-sale-summary")
  @CheckSecurity.FileProcessing.CanProcess
  public AcquirerSaleSummaryReconciliationResult reconcileAcquirerSaleSummary(
    @RequestParam(defaultValue = "false") boolean ignoreLookback
  ) {
    return acquirerSaleSummaryReconciliationService.reconcilePending(FinancialReconciliationTriggerType.MANUAL, ignoreLookback);
  }
}