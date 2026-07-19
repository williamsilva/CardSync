package com.cardsync.bff.controller.v1;

import com.cardsync.bff.controller.v1.representation.model.EmailSettingsModel;
import com.cardsync.bff.controller.v1.representation.model.EmailSettingsRequest;
import com.cardsync.core.config.EmailSettingsService;
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
@RequestMapping("/bff/v1/email/settings")
public class EmailSettingsController {

  private final EmailSettingsService emailSettingsService;

  @GetMapping
  @CheckSecurity.Settings.EmailSettings.CanConsult
  public EmailSettingsModel getSettings() {
    return emailSettingsService.getSettings();
  }

  @PutMapping
  @CheckSecurity.Settings.EmailSettings.CanProcess
  public EmailSettingsModel updateSettings(@Valid @RequestBody EmailSettingsRequest request) {
    return emailSettingsService.update(request);
  }
}
