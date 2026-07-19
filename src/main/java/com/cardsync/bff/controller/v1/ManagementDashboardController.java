package com.cardsync.bff.controller.v1;

import com.cardsync.bff.controller.v1.representation.model.management.ManagementDashboardModel;
import com.cardsync.bff.controller.v1.representation.model.management.ManagementDashboardRequest;
import com.cardsync.core.management.dashboard.ManagementDashboardService;
import com.cardsync.core.security.CheckSecurity;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/bff/v1/management")
public class ManagementDashboardController {

  private final ManagementDashboardService managementDashboardService;

  @PostMapping("/dashboard")
  @CheckSecurity.Management.ManagementDashboard.CanConsult
  public ManagementDashboardModel dashboard(@RequestBody ManagementDashboardRequest request) {
    return managementDashboardService.dashboard(request);
  }
}