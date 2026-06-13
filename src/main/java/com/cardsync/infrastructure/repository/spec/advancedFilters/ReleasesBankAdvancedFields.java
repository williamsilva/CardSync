package com.cardsync.infrastructure.repository.spec.advancedFilters;

import com.cardsync.domain.filter.ReleasesBankFilter;
import com.cardsync.domain.model.ReleasesBankEntity;
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

    spec = spec.and(inPath(filter.flags(), ReleasesBankAdvancedFields::parseUuidOrNull,"flag", "id"));
    spec = spec.and(inPath(filter.companies(), ReleasesBankAdvancedFields::parseUuidOrNull,"company", "id"));
    spec = spec.and(inPath(filter.acquirers(), ReleasesBankAdvancedFields::parseUuidOrNull,"acquirer", "id"));

    return spec;
  }
}