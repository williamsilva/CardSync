package com.cardsync.core.config;

import com.cardsync.bff.controller.v1.representation.model.EmailSettingsModel;
import com.cardsync.bff.controller.v1.representation.model.EmailSettingsRequest;
import com.cardsync.domain.model.EmailSettingsEntity;
import com.cardsync.domain.repository.EmailSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EmailSettingsService {

  private final EmailSettingsRepository repository;
  private final EmailProperties emailProperties;

  @Transactional(readOnly = true)
  public EmailSettingsModel getSettings() {
    return repository.findFirstBy()
      .map(e -> new EmailSettingsModel(e.getImpl(), e.getFromName(), e.getFromEmail(), e.getBrevoApiKey(), e.getBrevoBaseUrl()))
      .orElse(new EmailSettingsModel(null, null, null, null, null));
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

  @CacheEvict(value = "email-settings", allEntries = true)
  @Transactional
  public EmailSettingsModel update(EmailSettingsRequest request) {
    EmailSettingsEntity settings = repository.findFirstBy().orElseGet(EmailSettingsEntity::new);
    settings.setImpl(request.impl());
    settings.setFromName(request.fromName());
    settings.setFromEmail(request.fromEmail());
    settings.setBrevoApiKey(request.brevoApiKey());
    settings.setBrevoBaseUrl(request.brevoBaseUrl());
    settings = repository.save(settings);
    return new EmailSettingsModel(
      settings.getImpl(),
      settings.getFromName(),
      settings.getFromEmail(),
      settings.getBrevoApiKey(),
      settings.getBrevoBaseUrl()
    );
  }
}
