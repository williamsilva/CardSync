package com.cardsync.core.conciliation;

import com.cardsync.bff.controller.v1.representation.model.conciliation.ReconciliationSettingsModel;
import com.cardsync.bff.controller.v1.representation.model.conciliation.ReconciliationSettingsRequest;
import com.cardsync.domain.model.ReconciliationSettingsEntity;
import com.cardsync.domain.repository.ReconciliationSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReconciliationSettingsService {

  private final ReconciliationSettingsRepository repository;

  @Transactional(readOnly = true)
  public ReconciliationSettingsModel getSettings() {
    return repository.findFirstBy()
      .map(s -> new ReconciliationSettingsModel(
        s.getErpAcquirerPreviousDaysLookback(),
        s.getErpAcquirerFutureDaysLookback(),
        s.getReconciliationLookbackMonths()))
      .orElse(new ReconciliationSettingsModel(0, 0, 0));
  }

  @Cacheable(value = "reconciliation-settings", key = "#root.methodName")
  @Transactional(readOnly = true)
  public int getErpAcquirerPreviousDaysLookback() {
    return repository.findFirstBy()
      .map(ReconciliationSettingsEntity::getErpAcquirerPreviousDaysLookback)
      .orElse(0);
  }

  @Cacheable(value = "reconciliation-settings", key = "#root.methodName")
  @Transactional(readOnly = true)
  public int getErpAcquirerFutureDaysLookback() {
    return repository.findFirstBy()
      .map(ReconciliationSettingsEntity::getErpAcquirerFutureDaysLookback)
      .orElse(0);
  }

  @Cacheable(value = "reconciliation-settings", key = "#root.methodName")
  @Transactional(readOnly = true)
  public int getReconciliationLookbackMonths() {
    return repository.findFirstBy()
      .map(ReconciliationSettingsEntity::getReconciliationLookbackMonths)
      .orElse(0);
  }

  @CacheEvict(value = "reconciliation-settings", allEntries = true)
  @Transactional
  public ReconciliationSettingsModel update(ReconciliationSettingsRequest request) {
    ReconciliationSettingsEntity settings = repository.findFirstBy()
      .orElseGet(ReconciliationSettingsEntity::new);
    settings.setErpAcquirerPreviousDaysLookback(request.erpAcquirerPreviousDaysLookback());
    settings.setErpAcquirerFutureDaysLookback(request.erpAcquirerFutureDaysLookback());
    settings.setReconciliationLookbackMonths(request.reconciliationLookbackMonths());
    settings = repository.save(settings);
    return new ReconciliationSettingsModel(
      settings.getErpAcquirerPreviousDaysLookback(),
      settings.getErpAcquirerFutureDaysLookback(),
      settings.getReconciliationLookbackMonths());
  }
}
