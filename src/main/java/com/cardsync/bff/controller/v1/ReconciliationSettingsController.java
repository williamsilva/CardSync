package com.cardsync.bff.controller.v1;

import com.cardsync.bff.controller.v1.representation.model.conciliation.ReconciliationSettingsModel;
import com.cardsync.bff.controller.v1.representation.model.conciliation.ReconciliationSettingsRequest;
import com.cardsync.core.conciliation.ReconciliationSettingsService;
import com.cardsync.core.security.CheckSecurity;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/bff/v1/reconciliation/settings")
public class ReconciliationSettingsController {

  private final ReconciliationSettingsService reconciliationSettingsService;

  @GetMapping
  @CheckSecurity.Settings.ReconciliationSettings.CanConsult
  public ReconciliationSettingsModel getSettings() {
    return reconciliationSettingsService.getSettings();
  }

  @PutMapping
  @CheckSecurity.Settings.ReconciliationSettings.CanProcess
  public ReconciliationSettingsModel updateSettings(@Valid @RequestBody ReconciliationSettingsRequest request) {
    return reconciliationSettingsService.update(request);
  }
}
