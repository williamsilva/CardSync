package com.cardsync.infrastructure.repository.spec.advancedFilters;

import com.cardsync.domain.filter.ReleasesBankFilter;
import com.cardsync.domain.model.ReleasesBankEntity;
import com.cardsync.domain.model.enums.ModalityPaymentBankEnum;
import com.cardsync.domain.model.enums.ReleaseCategoryEnum;
import com.cardsync.domain.model.enums.StatusPaymentBankEnum;
import com.cardsync.infrastructure.repository.spec.config.BaseSpecificationSupport;
import com.cardsync.infrastructure.repository.spec.config.DateFilterService;
import com.cardsync.infrastructure.repository.spec.config.Specs;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

@Component
public class ReleasesBankAdvancedFields extends BaseSpecificationSupport<ReleasesBankEntity> {

  public ReleasesBankAdvancedFields(DateFilterService dateFilterService) {
    super(dateFilterService);
  }

  public Specification<ReleasesBankEntity> advanced(ReleasesBankFilter filter) {
    if (filter == null) {
      return Specs.all();
    }
    Specification<ReleasesBankEntity> spec = Specs.all();

    spec = spec.and(equalsTo("id", filter.id()));
    spec = spec.and(inCodes("releaseCategory", filter.releaseCategory(), ReleaseCategoryEnum::getCode));
    spec = spec.and(inCodes("reconciliationStatus", filter.statusPaymentBank(), StatusPaymentBankEnum::getCode));
    spec = spec.and(inCodes("modalityPaymentBank", filter.modalityPaymentBank(), ModalityPaymentBankEnum::getCode));

    spec = spec.and(currencyRangeValue("releaseValue", filter.releaseValueStart(), filter.releaseValueEnd()));

    spec = spec.and(inPath(filter.banks(), ReleasesBankAdvancedFields::parseUuidOrNull,"bank", "id"));
    spec = spec.and(inPath(filter.flags(), ReleasesBankAdvancedFields::parseUuidOrNull,"flag", "id"));
    spec = spec.and(inPath(filter.companies(), ReleasesBankAdvancedFields::parseUuidOrNull,"company", "id"));
    spec = spec.and(inPath(filter.acquirers(), ReleasesBankAdvancedFields::parseUuidOrNull,"acquirer", "id"));

    spec = spec.and(localDatePeriod("releaseDate", filter.periodReleaseDate(), filter.releaseDate(), true));
    spec = spec.and(hasDivergence(filter.hasDivergence()));

    return spec;
  }

  /** Só filtra quando true — lançamentos vinculados manualmente com diferença de valor aceita
   * (ver ManualBankReconciliationService.reconcile / ReleasesBankEntity.divergenceValue). */
  private Specification<ReleasesBankEntity> hasDivergence(Boolean value) {
    if (value == null || !value) {
      return Specs.all();
    }
    return (root, query, cb) -> cb.isNotNull(root.get("divergenceValue"));
  }
}