package com.cardsync.bff.controller.v1;

import com.cardsync.bff.controller.v1.representation.model.conciliation.*;
import com.cardsync.core.conciliation.analysis.ConciliationAnalysisService;
import com.cardsync.core.security.CheckSecurity;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

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

  @GetMapping("/fees")
  @CheckSecurity.FileProcessing.CanRead
  public Page<ConciliationFeeAnalysisModel> listFees(Pageable pageable) {
    return conciliationAnalysisService.listFees(pageable);
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
