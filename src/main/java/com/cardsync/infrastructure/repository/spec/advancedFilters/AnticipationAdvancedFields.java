package com.cardsync.infrastructure.repository.spec.advancedFilters;

import com.cardsync.domain.filter.AnticipationFilter;
import com.cardsync.domain.model.AnticipationEntity;
import com.cardsync.infrastructure.repository.spec.config.BaseSpecificationSupport;
import com.cardsync.infrastructure.repository.spec.config.DateFilterService;
import com.cardsync.infrastructure.repository.spec.config.Specs;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

@Component
public class AnticipationAdvancedFields extends BaseSpecificationSupport<AnticipationEntity> {

  public AnticipationAdvancedFields(DateFilterService dateFilterService) {
    super(dateFilterService);
  }

  public Specification<AnticipationEntity> advanced(AnticipationFilter filter) {
    if (filter == null) {
      return Specs.all();
    }
    Specification<AnticipationEntity> spec = Specs.all();

    spec = spec.and(inPath(filter.flags(), AnticipationAdvancedFields::parseUuidOrNull,"flag", "id"));
    spec = spec.and(inPath(filter.companies(), AnticipationAdvancedFields::parseUuidOrNull,"company", "id"));
    spec = spec.and(inPath(filter.acquirers(), AnticipationAdvancedFields::parseUuidOrNull,"acquirer", "id"));
    spec = spec.and(inPath(filter.establishments(), AnticipationAdvancedFields::parseUuidOrNull,"establishment", "id"));

    return spec;
  }
}
