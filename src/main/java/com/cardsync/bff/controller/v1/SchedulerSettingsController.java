package com.cardsync.bff.controller.v1;

import com.cardsync.bff.controller.v1.representation.model.conciliation.SchedulerSettingsModel;
import com.cardsync.bff.controller.v1.representation.model.conciliation.SchedulerSettingsRequest;
import com.cardsync.core.file.scheduler.SchedulerSettingsService;
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
@RequestMapping("/bff/v1/scheduler/settings")
public class SchedulerSettingsController {

  private final SchedulerSettingsService schedulerSettingsService;

  @GetMapping
  @CheckSecurity.Settings.SchedulerSettings.CanConsult
  public SchedulerSettingsModel getSettings() {
    return schedulerSettingsService.getSettings();
  }

  @PutMapping
  @CheckSecurity.Settings.SchedulerSettings.CanProcess
  public SchedulerSettingsModel updateSettings(@Valid @RequestBody SchedulerSettingsRequest request) {
    return schedulerSettingsService.update(request);
  }
}
