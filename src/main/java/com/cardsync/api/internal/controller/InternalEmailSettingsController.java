package com.cardsync.api.internal.controller;

import com.cardsync.bff.controller.v1.representation.model.EmailSettingsModel;
import com.cardsync.core.config.EmailSettingsService;
import com.nimbussystems.commons.legacy.mail.EmailSettingsRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API interna machine-to-machine (rota /internal/email-settings/**, ver
 *  InternalBackupSecretFilter/internalEmailSettingsChain em SecurityConfig) - consumida pelo
 *  NimbusAuth pra centralizar a tela "E-mail dos Apps". Espelha exatamente o
 *  EmailSettingsController (BFF) já existente, só sem a autenticação de sessão - a proteção aqui é
 *  só o secret compartilhado. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/email-settings")
public class InternalEmailSettingsController {

  private final EmailSettingsService emailSettingsService;

  @GetMapping
  public EmailSettingsModel get() {
    return emailSettingsService.getSettings();
  }

  @PutMapping
  public EmailSettingsModel update(@RequestBody EmailSettingsRequest request) {
    return emailSettingsService.update(request);
  }
}
