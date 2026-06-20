package com.cardsync.bff.controller.v1;

import com.cardsync.bff.controller.v1.representation.model.conciliation.*;
import com.cardsync.bff.controller.v1.representation.model.transactions.TransactionTotalsModel;
import com.cardsync.core.conciliation.analysis.ConciliationAnalysisService;
import com.cardsync.core.conciliation.analysis.ConciliationManualSwapReconciliationService;
import com.cardsync.core.conciliation.analysis.ErpAcquirerResolutionService;
import com.cardsync.core.security.CheckSecurity;
import com.cardsync.domain.filter.ConciliationWaitingModelFilter;
import com.cardsync.domain.filter.query.ListQueryDto;
import com.cardsync.domain.filter.support.PageableMapper;
import com.cardsync.domain.service.ConciliationWaitingService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.hateoas.PagedModel;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/bff/v1/conciliation-waiting")
public class ConciliationWaitingController {

  private final ConciliationWaitingService conciliationWaitingService;
  private final ConciliationAnalysisService conciliationAnalysisService;
  private final ErpAcquirerResolutionService erpAcquirerResolutionService;
  private final ConciliationManualSwapReconciliationService conciliationManualSwapReconciliationService;

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
    return conciliationAnalysisService.reconcileErpWithAcquirerBusinessContext();
  }

  @PostMapping("/reconcile-manual-swapped")
  @CheckSecurity.FileProcessing.CanProcess
  public ReconcileErpAcquirerResultModel reconcileManualSwapped() {
    return conciliationManualSwapReconciliationService.reconcileManualSwapped();
  }

  @PostMapping("/reconcile-fees")
  @CheckSecurity.FileProcessing.CanProcess
  public ReconcileErpAcquirerFeesResultModel reconcileErpAcquirerFees() {
    return conciliationAnalysisService.reconcileErpAcquirerFees();
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
}