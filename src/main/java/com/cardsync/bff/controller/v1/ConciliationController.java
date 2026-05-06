package com.cardsync.bff.controller.v1;

import com.cardsync.bff.controller.v1.representation.model.conciliation.*;
import com.cardsync.core.conciliation.analysis.ConciliationAnalysisService;
import com.cardsync.core.security.CheckSecurity;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/bff/v1/conciliation")
public class ConciliationController {

  private final ConciliationAnalysisService conciliationAnalysisService;

  @GetMapping("/dashboard")
  @CheckSecurity.FileProcessing.CanRead
  public ConciliationDashboardModel dashboard() {
    return conciliationAnalysisService.dashboard();
  }

  @GetMapping("/acquirer-sales")
  @CheckSecurity.FileProcessing.CanRead
  public Page<AcquirerSaleAnalysisModel> listAcquirerSales(Pageable pageable) {
    return conciliationAnalysisService.listAcquirerSales(pageable);
  }

  @GetMapping("/fees")
  @CheckSecurity.FileProcessing.CanRead
  public Page<ConciliationFeeAnalysisModel> listFees(Pageable pageable) {
    return conciliationAnalysisService.listFees(pageable);
  }

  @GetMapping("/erp-vs-acquirer")
  @CheckSecurity.FileProcessing.CanRead
  public Page<ErpVsAcquirerAnalysisModel> listErpVsAcquirer(Pageable pageable) {
    return conciliationAnalysisService.listErpVsAcquirer(pageable);
  }

  @PostMapping("/erp-vs-acquirer/reconcile")
  @CheckSecurity.FileProcessing.CanRead
  public ReconcileErpAcquirerResultModel reconcileErpVsAcquirer() {
    return conciliationAnalysisService.reconcileErpWithAcquirerBusinessContext();
  }

  @GetMapping("/debits")
  @CheckSecurity.FileProcessing.CanRead
  public Page<DebitAnalysisModel> listDebits(Pageable pageable) {
    return conciliationAnalysisService.listDebits(pageable);
  }

  @GetMapping("/chargebacks")
  @CheckSecurity.FileProcessing.CanRead
  public Page<ChargebackAnalysisModel> listChargebacks(Pageable pageable) {
    return conciliationAnalysisService.listChargebacks(pageable);
  }

  @GetMapping("/bank-settlement")
  @CheckSecurity.FileProcessing.CanRead
  public Page<BankSettlementAnalysisModel> listBankSettlement(Pageable pageable) {
    return conciliationAnalysisService.listBankSettlement(pageable);
  }

  @GetMapping("/divergences")
  @CheckSecurity.FileProcessing.CanRead
  public Page<DivergenceAnalysisModel> listDivergences(Pageable pageable) {
    return conciliationAnalysisService.listDivergences(pageable);
  }

  @GetMapping("/aging")
  @CheckSecurity.FileProcessing.CanRead
  public List<ConciliationAgingModel> listAging() {
    return conciliationAnalysisService.aging();
  }
}
