package com.cardsync.core.config;

import com.cardsync.bff.controller.v1.representation.model.EmailSettingsModel;
import com.nimbussystems.commons.legacy.mail.EmailSettingsRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/** Composer fino, não repositório próprio: campos padrão (impl/remetente/Brevo/SMTP) vêm do
 *  EmailSettingsService compartilhado (nimbus-commons-server); chargebackRecipients (sem
 *  equivalente na lib, mas em uso real - ver ChargebackEmailService) vem à parte do
 *  ChargebackRecipientsRepository (JDBC direto na mesma tabela "email_settings"). Mantido com o
 *  mesmo nome/pacote de antes (só a implementação por trás mudou) - o BffEmailSettingsController
 *  local e o ChargebackEmailService não precisam de nenhuma mudança. */
// Bean name explícito ("cardsyncEmailSettingsService") - o nome default ("emailSettingsService")
// colide com o bean da lib compartilhada (com.nimbussystems.commons.notification.mail.
// EmailSettingsService, injetado abaixo). São classes diferentes (sem ambiguidade de TIPO pra
// quem injeta esta aqui, ex. EmailSettingsController/ChargebackEmailService), só o NOME do
// registro de bean colidia.
@Service("cardsyncEmailSettingsService")
@RequiredArgsConstructor
public class EmailSettingsService {

  private final com.nimbussystems.commons.notification.mail.EmailSettingsService shared;
  private final ChargebackRecipientsRepository chargebackRepo;

  public EmailSettingsModel getSettings() {
    var s = shared.getSettings();
    return new EmailSettingsModel(
      s.impl(), s.allowFakeImpl(), s.fromName(), s.fromEmail(),
      s.brevoApiKey(), s.brevoBaseUrl(), s.brevoPort(), s.brevoUsername(),
      chargebackRepo.getRaw(),
      s.smtpHost(), s.smtpPort(), s.smtpUsername(), s.smtpPassword(),
      s.smtpAuth(), s.smtpStarttls(), s.smtpSsl()
    );
  }

  public EmailSettingsModel update(EmailSettingsRequest request) {
    shared.update(new com.nimbussystems.commons.notification.mail.EmailSettingsRequest(
      request.impl(), request.fromName(), request.fromEmail(),
      request.brevoApiKey(), request.brevoBaseUrl(), request.brevoPort(), request.brevoUsername(),
      request.smtpHost(), request.smtpPort(), request.smtpUsername(), request.smtpPassword(),
      request.smtpAuth(), request.smtpStarttls(), request.smtpSsl()
    ));
    chargebackRepo.update(request.chargebackRecipients());
    return getSettings();
  }

  /** Único uso real hoje: ChargebackEmailService.notifyChargebacksFound. */
  public List<String> getChargebackRecipients() {
    return chargebackRepo.getAsList();
  }
}
