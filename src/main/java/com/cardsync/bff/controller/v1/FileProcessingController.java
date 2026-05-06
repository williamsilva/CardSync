package com.cardsync.bff.controller.v1;

import com.cardsync.bff.controller.v1.representation.model.bank.BankReleaseModel;
import com.cardsync.bff.controller.v1.representation.model.erp.ErpPendingSaleModel;
import com.cardsync.bff.controller.v1.representation.model.fileprocessing.FileProcessingScheduleStatusModel;
import com.cardsync.bff.controller.v1.representation.model.fileprocessing.ProcessedFileErrorModel;
import com.cardsync.bff.controller.v1.representation.model.fileprocessing.ProcessedFileModel;
import com.cardsync.bff.controller.v1.representation.model.fileprocessing.ProcessedFileSummaryModel;
import com.cardsync.bff.controller.v1.representation.model.fileprocessing.ReprocessPendingErpResultModel;
import com.cardsync.bff.controller.v1.representation.model.fileprocessing.dashboard.FileProcessingDashboardModel;
import com.cardsync.bff.controller.v1.representation.model.fileprocessing.dashboard.FileProcessingDivergenceContextModel;
import com.cardsync.bff.controller.v1.representation.model.rede.RedeAdjustmentModel;
import com.cardsync.bff.controller.v1.representation.model.rede.RedeAnticipationModel;
import com.cardsync.bff.controller.v1.representation.model.rede.RedeCreditOrderModel;
import com.cardsync.bff.controller.v1.representation.model.rede.RedePendingDebtModel;
import com.cardsync.bff.controller.v1.representation.model.rede.RedeSettledDebtModel;
import com.cardsync.bff.controller.v1.representation.model.rede.RedeTotalizerModel;
import com.cardsync.core.file.bank.service.BankReleaseQueryService;
import com.cardsync.core.file.erp.service.ErpPendingSaleService;
import com.cardsync.core.file.rede.service.RedeFinancialQueryService;
import com.cardsync.core.file.service.FileStorageTask;
import com.cardsync.core.file.service.dashboard.FileProcessingDashboardService;
import com.cardsync.core.file.service.report.FileProcessingReportService;
import com.cardsync.core.reconciliation.BankReconciliationResult;
import com.cardsync.core.reconciliation.BankReconciliationService;
import com.cardsync.core.security.CheckSecurity;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/bff/v1/file-processing")
public class FileProcessingController {

  private final FileStorageTask fileStorageTask;
  private final FileProcessingReportService reportService;
  private final ErpPendingSaleService erpPendingSaleService;
  private final RedeFinancialQueryService redeFinancialQueryService;
  private final FileProcessingDashboardService dashboardService;
  private final BankReleaseQueryService bankReleaseQueryService;
  private final BankReconciliationService bankReconciliationService;

  @GetMapping("/dashboard")
  @CheckSecurity.FileProcessing.CanRead
  public FileProcessingDashboardModel dashboard() {
    return dashboardService.dashboard();
  }

  @GetMapping("/dashboard/divergences")
  @CheckSecurity.FileProcessing.CanRead
  public List<FileProcessingDivergenceContextModel> dashboardDivergences() {
    return dashboardService.divergenceContexts();
  }

  @GetMapping("/files")
  @CheckSecurity.FileProcessing.CanRead
  public Page<ProcessedFileModel> list(Pageable pageable) {
    return reportService.list(pageable);
  }

  @GetMapping("/files/{processedFileId}")
  @CheckSecurity.FileProcessing.CanRead
  public ProcessedFileModel find(@PathVariable UUID processedFileId) {
    return reportService.find(processedFileId);
  }

  @GetMapping("/files/{processedFileId}/summary")
  @CheckSecurity.FileProcessing.CanRead
  public ProcessedFileSummaryModel summary(@PathVariable UUID processedFileId) {
    return reportService.summary(processedFileId);
  }

  @GetMapping("/files/{processedFileId}/errors")
  @CheckSecurity.FileProcessing.CanRead
  public List<ProcessedFileErrorModel> listErrors(@PathVariable UUID processedFileId) {
    return reportService.listErrors(processedFileId);
  }

  @GetMapping("/erp/pending-sales")
  @CheckSecurity.FileProcessing.CanRead
  public Page<ErpPendingSaleModel> listPendingErpSales(Pageable pageable) {
    return erpPendingSaleService.listPending(pageable);
  }

  @GetMapping("/erp/pending-sales/{id}")
  @CheckSecurity.FileProcessing.CanRead
  public ErpPendingSaleModel findPendingErpSale(@PathVariable UUID id) {
    return erpPendingSaleService.findPending(id);
  }

  @GetMapping("/rede/credit-orders")
  @CheckSecurity.FileProcessing.CanRead
  public Page<RedeCreditOrderModel> listRedeCreditOrders(Pageable pageable) {
    return redeFinancialQueryService.listCreditOrders(pageable);
  }

  @GetMapping("/rede/adjustments")
  @CheckSecurity.FileProcessing.CanRead
  public Page<RedeAdjustmentModel> listRedeAdjustments(Pageable pageable) {
    return redeFinancialQueryService.listAdjustments(pageable);
  }

  @GetMapping("/rede/anticipations")
  @CheckSecurity.FileProcessing.CanRead
  public Page<RedeAnticipationModel> listRedeAnticipations(Pageable pageable) {
    return redeFinancialQueryService.listAnticipations(pageable);
  }

  @GetMapping("/rede/settled-debts")
  @CheckSecurity.FileProcessing.CanRead
  public Page<RedeSettledDebtModel> listRedeSettledDebts(Pageable pageable) {
    return redeFinancialQueryService.listSettledDebts(pageable);
  }

  @GetMapping("/rede/pending-debts")
  @CheckSecurity.FileProcessing.CanRead
  public Page<RedePendingDebtModel> listRedePendingDebts(Pageable pageable) {
    return redeFinancialQueryService.listPendingDebts(pageable);
  }

  @GetMapping("/rede/totalizers")
  @CheckSecurity.FileProcessing.CanRead
  public Page<RedeTotalizerModel> listRedeTotalizers(Pageable pageable) {
    return redeFinancialQueryService.listTotalizers(pageable);
  }

  @GetMapping("/bank/releases")
  @CheckSecurity.FileProcessing.CanRead
  public Page<BankReleaseModel> listBankReleases(Pageable pageable) {
    return bankReleaseQueryService.list(pageable);
  }

  @GetMapping("/schedules/status")
  @CheckSecurity.FileProcessing.CanRead
  public Map<String, FileProcessingScheduleStatusModel> scheduleStatus() {
    return Map.of(
      "erp", FileProcessingScheduleStatusModel.from(fileStorageTask.erpStatus()),
      "rede", FileProcessingScheduleStatusModel.from(fileStorageTask.redeStatus()),
      "bank", FileProcessingScheduleStatusModel.from(fileStorageTask.bankStatus())
    );
  }

  @PostMapping("/erp/process")
  @CheckSecurity.FileProcessing.CanProcess
  public ResponseEntity<Void> processErp() {
    fileStorageTask.processFileErp();
    return ResponseEntity.accepted().build();
  }

  @PostMapping("/erp/reprocess-pending")
  @CheckSecurity.FileProcessing.CanReprocess
  public ReprocessPendingErpResultModel reprocessPendingErpSales() {
    return erpPendingSaleService.reprocessPending();
  }

  @PostMapping("/rede/process")
  @CheckSecurity.FileProcessing.CanProcess
  public ResponseEntity<Void> processRede() {
    fileStorageTask.processFileRede();
    return ResponseEntity.accepted().build();
  }

  @PostMapping("/bank/process")
  @CheckSecurity.FileProcessing.CanProcess
  public ResponseEntity<Void> processBank() {
    fileStorageTask.processFileBank();
    return ResponseEntity.accepted().build();
  }

  @PostMapping("/bank/reconcile")
  @CheckSecurity.FileProcessing.CanProcess
  public BankReconciliationResult reconcileBank() {
    return bankReconciliationService.reconcilePending();
  }
}
