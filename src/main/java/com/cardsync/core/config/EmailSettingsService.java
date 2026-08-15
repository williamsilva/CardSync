package com.cardsync.core.config;

import com.cardsync.bff.controller.v1.representation.model.EmailSettingsModel;
import com.cardsync.bff.controller.v1.representation.model.EmailSettingsRequest;
import com.cardsync.domain.model.EmailSettingsEntity;
import com.cardsync.domain.repository.EmailSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EmailSettingsService {

  private final EmailSettingsRepository repository;
  private final EmailProperties emailProperties;
  private final Environment environment;

  @Transactional(readOnly = true)
  public EmailSettingsModel getSettings() {
    boolean allowFake = allowFakeImpl();
    return repository.findFirstBy()
      .map(e -> new EmailSettingsModel(
        e.getImpl(), allowFake, e.getFromName(), e.getFromEmail(),
        mask(e.getBrevoApiKey()), e.getBrevoBaseUrl(), e.getBrevoPort(), e.getBrevoUsername(),
        e.getChargebackRecipients(),
        e.getSmtpHost(), e.getSmtpPort(), e.getSmtpUsername(), mask(e.getSmtpPassword()),
        e.getSmtpAuth(), e.getSmtpStarttls(), e.getSmtpSsl()
      ))
      .orElse(new EmailSettingsModel(null, allowFake, null, null, null, null, null, null, null, null, null, null, null, null, null, null));
  }

  /** true fora do perfil "prod" (dev/test/local/sem perfil) - usado só pra tela esconder a opção
   *  FAKE do seletor em produção (ver EmailSettingsModel.allowFakeImpl). Não bloqueia nada no
   *  backend - EmailSenderServiceRouter continua aceitando FAKE mesmo em prod se alguém já tiver
   *  esse valor salvo no banco de antes, só a tela nova esconde a opção de escolher de novo. */
  private boolean allowFakeImpl() {
    return !environment.acceptsProfiles(Profiles.of("prod"));
  }

  /**
   * Nunca devolve segredo (chave de API, senha) em texto puro pra tela — só indica que um
   * valor está configurado, mostrando os 4 últimos caracteres como referência visual. O valor
   * real só é usado internamente (ver getBrevoApiKey()/getSmtpPassword() acima, que alimentam
   * o cliente de envio de e-mail de verdade, não esta resposta HTTP).
   */
  private String mask(String secret) {
    if (secret == null || secret.isBlank()) return null;
    int visible = Math.min(4, secret.length());
    return "•".repeat(Math.max(secret.length() - visible, 6)) + secret.substring(secret.length() - visible);
  }

  @Cacheable(value = "email-settings", key = "#root.methodName")
  @Transactional(readOnly = true)
  public EmailProperties.Impl getImpl() {
    return repository.findFirstBy()
      .map(e -> e.getImpl() != null ? EmailProperties.Impl.valueOf(e.getImpl().toUpperCase()) : emailProperties.getImpl())
      .orElse(emailProperties.getImpl());
  }

  @Cacheable(value = "email-settings", key = "#root.methodName")
  @Transactional(readOnly = true)
  public String getFromName() {
    return repository.findFirstBy()
      .map(e -> e.getFromName() != null ? e.getFromName() : emailProperties.getFromName())
      .orElse(emailProperties.getFromName());
  }

  @Cacheable(value = "email-settings", key = "#root.methodName")
  @Transactional(readOnly = true)
  public String getFromEmail() {
    return repository.findFirstBy()
      .map(e -> e.getFromEmail() != null ? e.getFromEmail() : emailProperties.getFromEmail())
      .orElse(emailProperties.getFromEmail());
  }

  @Cacheable(value = "email-settings", key = "#root.methodName")
  @Transactional(readOnly = true)
  public String getBrevoApiKey() {
    return repository.findFirstBy()
      .map(e -> e.getBrevoApiKey() != null ? e.getBrevoApiKey() : emailProperties.getBrevo().getApiKey())
      .orElse(emailProperties.getBrevo().getApiKey());
  }

  @Cacheable(value = "email-settings", key = "#root.methodName")
  @Transactional(readOnly = true)
  public String getBrevoBaseUrl() {
    return repository.findFirstBy()
      .map(e -> e.getBrevoBaseUrl() != null ? e.getBrevoBaseUrl() : emailProperties.getBrevo().getBaseUrl())
      .orElse(emailProperties.getBrevo().getBaseUrl());
  }

  @Cacheable(value = "email-settings", key = "#root.methodName")
  @Transactional(readOnly = true)
  public Integer getBrevoPort() {
    return repository.findFirstBy()
      .map(e -> e.getBrevoPort() != null ? e.getBrevoPort() : emailProperties.getBrevo().getPort())
      .orElse(emailProperties.getBrevo().getPort());
  }

  @Cacheable(value = "email-settings", key = "#root.methodName")
  @Transactional(readOnly = true)
  public String getBrevoUsername() {
    return repository.findFirstBy()
      .map(e -> e.getBrevoUsername() != null ? e.getBrevoUsername() : emailProperties.getBrevo().getUsername())
      .orElse(emailProperties.getBrevo().getUsername());
  }

  @Cacheable(value = "email-settings", key = "#root.methodName")
  @Transactional(readOnly = true)
  public List<String> getChargebackRecipients() {
    return repository.findFirstBy()
      .map(EmailSettingsEntity::getChargebackRecipients)
      .filter(v -> v != null && !v.isBlank())
      .map(v -> Arrays.stream(v.split(","))
        .map(String::trim)
        .filter(s -> !s.isBlank())
        .toList())
      .orElse(List.of());
  }

  @Cacheable(value = "email-settings", key = "#root.methodName")
  @Transactional(readOnly = true)
  public String getSmtpHost() {
    return repository.findFirstBy()
      .map(e -> e.getSmtpHost() != null ? e.getSmtpHost() : emailProperties.getSmtp().getHost())
      .orElse(emailProperties.getSmtp().getHost());
  }

  @Cacheable(value = "email-settings", key = "#root.methodName")
  @Transactional(readOnly = true)
  public Integer getSmtpPort() {
    return repository.findFirstBy()
      .map(e -> e.getSmtpPort() != null ? e.getSmtpPort() : emailProperties.getSmtp().getPort())
      .orElse(emailProperties.getSmtp().getPort());
  }

  @Cacheable(value = "email-settings", key = "#root.methodName")
  @Transactional(readOnly = true)
  public String getSmtpUsername() {
    return repository.findFirstBy()
      .map(e -> e.getSmtpUsername() != null ? e.getSmtpUsername() : emailProperties.getSmtp().getUsername())
      .orElse(emailProperties.getSmtp().getUsername());
  }

  @Cacheable(value = "email-settings", key = "#root.methodName")
  @Transactional(readOnly = true)
  public String getSmtpPassword() {
    return repository.findFirstBy()
      .map(e -> e.getSmtpPassword() != null ? e.getSmtpPassword() : emailProperties.getSmtp().getPassword())
      .orElse(emailProperties.getSmtp().getPassword());
  }

  @Cacheable(value = "email-settings", key = "#root.methodName")
  @Transactional(readOnly = true)
  public Boolean getSmtpAuth() {
    return repository.findFirstBy()
      .map(e -> e.getSmtpAuth() != null ? e.getSmtpAuth() : emailProperties.getSmtp().getAuth())
      .orElse(emailProperties.getSmtp().getAuth());
  }

  @Cacheable(value = "email-settings", key = "#root.methodName")
  @Transactional(readOnly = true)
  public Boolean getSmtpStarttls() {
    return repository.findFirstBy()
      .map(e -> e.getSmtpStarttls() != null ? e.getSmtpStarttls() : emailProperties.getSmtp().getStarttls())
      .orElse(emailProperties.getSmtp().getStarttls());
  }

  @Cacheable(value = "email-settings", key = "#root.methodName")
  @Transactional(readOnly = true)
  public Boolean getSmtpSsl() {
    return repository.findFirstBy()
      .map(e -> e.getSmtpSsl() != null ? e.getSmtpSsl() : emailProperties.getSmtp().getSsl())
      .orElse(emailProperties.getSmtp().getSsl());
  }

  @CacheEvict(value = "email-settings", allEntries = true)
  @Transactional
  public EmailSettingsModel update(EmailSettingsRequest request) {
    EmailSettingsEntity settings = repository.findFirstBy().orElseGet(EmailSettingsEntity::new);
    settings.setImpl(request.impl());
    settings.setFromName(request.fromName());
    settings.setFromEmail(request.fromEmail());
    // brevoApiKey/smtpPassword: a tela nunca recebe o valor real de volta (ver mask() em
    // getSettings()), então só troca o segredo salvo quando um valor novo de verdade é
    // enviado — campo vazio/omitido significa "não mudar", não "apagar o segredo".
    if (request.brevoApiKey() != null && !request.brevoApiKey().isBlank()) {
      settings.setBrevoApiKey(request.brevoApiKey());
    }
    settings.setBrevoBaseUrl(request.brevoBaseUrl());
    settings.setBrevoPort(request.brevoPort());
    settings.setBrevoUsername(request.brevoUsername());
    settings.setChargebackRecipients(request.chargebackRecipients());
    settings.setSmtpHost(request.smtpHost());
    settings.setSmtpPort(request.smtpPort());
    settings.setSmtpUsername(request.smtpUsername());
    if (request.smtpPassword() != null && !request.smtpPassword().isBlank()) {
      settings.setSmtpPassword(request.smtpPassword());
    }
    settings.setSmtpAuth(request.smtpAuth());
    settings.setSmtpStarttls(request.smtpStarttls());
    settings.setSmtpSsl(request.smtpSsl());
    settings = repository.save(settings);
    return new EmailSettingsModel(
      settings.getImpl(), allowFakeImpl(), settings.getFromName(), settings.getFromEmail(),
      mask(settings.getBrevoApiKey()), settings.getBrevoBaseUrl(), settings.getBrevoPort(), settings.getBrevoUsername(),
      settings.getChargebackRecipients(),
      settings.getSmtpHost(), settings.getSmtpPort(), settings.getSmtpUsername(), mask(settings.getSmtpPassword()),
      settings.getSmtpAuth(), settings.getSmtpStarttls(), settings.getSmtpSsl()
    );
  }
}
