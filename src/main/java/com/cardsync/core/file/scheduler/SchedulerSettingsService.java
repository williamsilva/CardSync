package com.cardsync.core.file.scheduler;

import com.cardsync.bff.controller.v1.representation.model.conciliation.SchedulerSettingsModel;
import com.cardsync.bff.controller.v1.representation.model.conciliation.SchedulerSettingsRequest;
import com.cardsync.domain.model.SchedulerSettingsEntity;
import com.cardsync.domain.repository.SchedulerSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SchedulerSettingsService {

  private final SchedulerSettingsRepository repository;

  @Transactional(readOnly = true)
  public SchedulerSettingsModel getSettings() {
    return repository.findFirstBy()
      .map(s -> new SchedulerSettingsModel(
        s.isEnabled(),
        s.isCompletePipelineEnabled(),
        s.getCompletePipelineCron(),
        s.isCompletePipelineStopOnStepError(),
        s.isLogIdleCycles()))
      .orElse(new SchedulerSettingsModel(false, true, "0 0/30 * * * *", true, false));
  }

  @Cacheable(value = "scheduler-settings", key = "#root.methodName")
  @Transactional(readOnly = true)
  public boolean isEnabled() {
    return repository.findFirstBy()
      .map(SchedulerSettingsEntity::isEnabled)
      .orElse(false);
  }

  @Cacheable(value = "scheduler-settings", key = "#root.methodName")
  @Transactional(readOnly = true)
  public boolean isCompletePipelineEnabled() {
    return repository.findFirstBy()
      .map(SchedulerSettingsEntity::isCompletePipelineEnabled)
      .orElse(true);
  }

  @Cacheable(value = "scheduler-settings", key = "#root.methodName")
  @Transactional(readOnly = true)
  public String getCompletePipelineCron() {
    return repository.findFirstBy()
      .map(SchedulerSettingsEntity::getCompletePipelineCron)
      .orElse("0 0/30 * * * *");
  }

  @Cacheable(value = "scheduler-settings", key = "#root.methodName")
  @Transactional(readOnly = true)
  public boolean isCompletePipelineStopOnStepError() {
    return repository.findFirstBy()
      .map(SchedulerSettingsEntity::isCompletePipelineStopOnStepError)
      .orElse(true);
  }

  @Cacheable(value = "scheduler-settings", key = "#root.methodName")
  @Transactional(readOnly = true)
  public boolean isLogIdleCycles() {
    return repository.findFirstBy()
      .map(SchedulerSettingsEntity::isLogIdleCycles)
      .orElse(false);
  }

  @CacheEvict(value = "scheduler-settings", allEntries = true)
  @Transactional
  public SchedulerSettingsModel update(SchedulerSettingsRequest request) {
    SchedulerSettingsEntity settings = repository.findFirstBy()
      .orElseGet(SchedulerSettingsEntity::new);
    settings.setEnabled(request.enabled());
    settings.setCompletePipelineEnabled(request.completePipelineEnabled());
    settings.setCompletePipelineCron(request.completePipelineCron());
    settings.setCompletePipelineStopOnStepError(request.completePipelineStopOnStepError());
    settings.setLogIdleCycles(request.logIdleCycles());
    settings = repository.save(settings);
    return new SchedulerSettingsModel(
      settings.isEnabled(),
      settings.isCompletePipelineEnabled(),
      settings.getCompletePipelineCron(),
      settings.isCompletePipelineStopOnStepError(),
      settings.isLogIdleCycles());
  }
}
