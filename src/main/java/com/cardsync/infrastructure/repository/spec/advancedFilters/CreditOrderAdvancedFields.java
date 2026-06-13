package com.cardsync.infrastructure.repository.spec.advancedFilters;

import com.cardsync.domain.filter.CreditOrderFilter;
import com.cardsync.domain.model.CreditOrderEntity;
import com.cardsync.infrastructure.repository.spec.config.BaseSpecificationSupport;
import com.cardsync.infrastructure.repository.spec.config.DateFilterService;
import com.cardsync.infrastructure.repository.spec.config.Specs;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;


@Component
public class CreditOrderAdvancedFields extends BaseSpecificationSupport<CreditOrderEntity> {

  public CreditOrderAdvancedFields(DateFilterService dateFilterService) {
    super(dateFilterService);
  }

  public Specification<CreditOrderEntity> advanced(CreditOrderFilter filter) {
    if (filter == null) {
      return Specs.all();
    }
    Specification<CreditOrderEntity> spec = Specs.all();

    return spec;
  }
}