package com.cardsync.bff.controller.v1;

import com.cardsync.bff.controller.v1.representation.model.conciliation.*;
import com.cardsync.core.conciliation.analysis.ConciliationAnalysisService;
import com.cardsync.core.security.CheckSecurity;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/bff/v1/conciliation")
public class ConciliationController {

  private final ConciliationAnalysisService conciliationAnalysisService;

  @GetMapping("/dashboard")
  @CheckSecurity.Reconciliation.ConciliationDashboard.CanConsult
  public ConciliationDashboardModel dashboard() {
    return conciliationAnalysisService.dashboard();
  }

  @GetMapping("/aging")
  @CheckSecurity.Reconciliation.ConciliationDashboard.CanConsult
  public List<ConciliationAgingModel> listAging() {
    return conciliationAnalysisService.aging();
  }
}
