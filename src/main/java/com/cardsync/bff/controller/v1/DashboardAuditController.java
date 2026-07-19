package com.cardsync.bff.controller.v1;

import com.cardsync.bff.controller.v1.representation.model.management.AuditSalesSummaryModel;
import com.cardsync.bff.controller.v1.representation.model.management.AuditUnreconciledModel;
import com.cardsync.core.management.dashboard.DashboardAuditService;
import com.cardsync.core.security.CheckSecurity;
import com.cardsync.domain.filter.ConciliationWaitingModelFilter;
import com.cardsync.domain.filter.query.ListQueryDto;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/bff/v1/management/dashboard-audit")
public class DashboardAuditController {

  private final DashboardAuditService dashboardAuditService;

  @GetMapping("/sales-summary")
  @CheckSecurity.Management.DashboardAudit.CanConsult
  public AuditSalesSummaryModel salesSummary() {
    return dashboardAuditService.salesSummary();
  }

  @PostMapping("/unreconciled")
  @CheckSecurity.Management.DashboardAudit.CanConsult
  public AuditUnreconciledModel unreconciled(@RequestBody(required = false) ListQueryDto<ConciliationWaitingModelFilter> body
  ) {
    return dashboardAuditService.unreconciled(body);
  }
}