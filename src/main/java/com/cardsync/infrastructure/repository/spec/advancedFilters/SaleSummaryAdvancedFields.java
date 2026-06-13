package com.cardsync.infrastructure.repository.spec.advancedFilters;

import com.cardsync.domain.filter.SaleSummaryFilter;
import com.cardsync.domain.model.SalesSummaryEntity;
import com.cardsync.infrastructure.repository.spec.config.BaseSpecificationSupport;
import com.cardsync.infrastructure.repository.spec.config.DateFilterService;
import com.cardsync.infrastructure.repository.spec.config.Specs;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

@Component
public class SaleSummaryAdvancedFields extends BaseSpecificationSupport<SalesSummaryEntity> {

  public SaleSummaryAdvancedFields(DateFilterService dateFilterService) {
    super(dateFilterService);
  }

  public Specification<SalesSummaryEntity> advanced(SaleSummaryFilter filter) {
    if (filter == null) {
      return Specs.all();
    }
    Specification<SalesSummaryEntity> spec = Specs.all();

    spec = spec.and(inPath(filter.flags(), SaleSummaryAdvancedFields::parseUuidOrNull,"flag", "id"));
    spec = spec.and(inPath(filter.companies(), SaleSummaryAdvancedFields::parseUuidOrNull,"company", "id"));
    spec = spec.and(inPath(filter.acquirers(), SaleSummaryAdvancedFields::parseUuidOrNull,"acquirer", "id"));

    return spec;
  }
}
