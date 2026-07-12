package com.cardsync.core.config;

import com.cardsync.domain.model.ReconciliationSettingsEntity;
import com.cardsync.domain.repository.ReconciliationSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Fonte única da data de implantação (go-live) do CardSync, armazenada em
 * cs_reconciliation_settings.go_live_date e editável na tela de configurações
 * de conciliação. Substitui a antiga propriedade cardsync.app.implantation-date.
 *
 * <p>Cacheada em "reconciliation-settings"; o cache é invalidado quando as
 * configurações são salvas ({@code ReconciliationSettingsService#update}).
 */
@Component
@RequiredArgsConstructor
public class ImplantationDateProvider {

  /** Default histórico, usado apenas enquanto não houver linha de configuração. */
  public static final LocalDate DEFAULT_IMPLANTATION_DATE = LocalDate.of(2024, 7, 1);

  private final ReconciliationSettingsRepository repository;

  @Cacheable(value = "reconciliation-settings", key = "'implantationDate'")
  @Transactional(readOnly = true)
  public LocalDate get() {
    return repository.findFirstBy()
      .map(ReconciliationSettingsEntity::getGoLiveDate)
      .orElse(DEFAULT_IMPLANTATION_DATE);
  }
}
