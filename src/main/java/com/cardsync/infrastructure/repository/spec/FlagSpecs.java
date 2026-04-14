package com.cardsync.infrastructure.repository.spec;

import com.cardsync.domain.filter.FlagFilter;
import com.cardsync.domain.filter.query.ListQueryDto;
import com.cardsync.domain.filter.spec.FlagAllowedFields;
import com.cardsync.domain.model.FlagEntity;
import com.cardsync.domain.model.enums.StatusEnum;
import com.cardsync.infrastructure.repository.spec.config.BaseSpecificationSupport;
import com.cardsync.infrastructure.repository.spec.config.DateFilterService;
import com.cardsync.infrastructure.repository.spec.config.SpecificationFactory;
import com.cardsync.infrastructure.repository.spec.config.Specs;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

@Component
public class FlagSpecs extends BaseSpecificationSupport<FlagEntity> {

  private final FlagAllowedFields flagAllowedFields;
  private final SpecificationFactory specificationFactory;

  public FlagSpecs(
    DateFilterService dateFilterService,
    SpecificationFactory specificationFactory,
    FlagAllowedFields flagAllowedFields
  ) {
    super(dateFilterService);
    this.specificationFactory = specificationFactory;
    this.flagAllowedFields = flagAllowedFields;
  }

  public Specification<FlagEntity> fromQuery(ListQueryDto<FlagFilter> query) {
    Specification<FlagEntity> spec = Specs.all();

    spec = spec.and(
      specificationFactory.fromTableFilters(
        query.tableFilters(),
        flagAllowedFields.table()
      )
    );

    if (query.advanced() != null) {
      var a = query.advanced();
      spec = spec.and(contains("name", a.name()));
      spec = spec.and(equalsTo("erpCode", a.erpCode()));

      spec = spec.and(inCodes("status", a.statusEnum(), StatusEnum::getCode));
    }

    return spec.and(orderByAsc("name"));
  }
}