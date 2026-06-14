package com.cardsync.infrastructure.repository.spec;

import com.cardsync.domain.filter.HolidayFilter;
import com.cardsync.domain.filter.query.ListQueryDto;
import com.cardsync.domain.model.HolidayEntity;
import com.cardsync.domain.model.enums.StatusEnum;
import com.cardsync.infrastructure.repository.spec.tableFilters.HolidayTableFields;
import com.cardsync.infrastructure.repository.spec.config.BaseSpecificationSupport;
import com.cardsync.infrastructure.repository.spec.config.DateFilterService;
import com.cardsync.infrastructure.repository.spec.config.SpecificationFactory;
import com.cardsync.infrastructure.repository.spec.config.Specs;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

@Component
public class HolidaySpecs extends BaseSpecificationSupport<HolidayEntity> {

  private final HolidayTableFields holidayTableFields;
  private final SpecificationFactory specificationFactory;

  public HolidaySpecs(
    DateFilterService dateFilterService,
    SpecificationFactory specificationFactory,
    HolidayTableFields holidayTableFields
  ) {
    super(dateFilterService);
    this.specificationFactory = specificationFactory;
    this.holidayTableFields = holidayTableFields;
  }

  public Specification<HolidayEntity> fromQuery(ListQueryDto<HolidayFilter> query) {
    Specification<HolidayEntity> spec = Specs.all();

    spec = spec.and(
      specificationFactory.fromTableFilters(
        query.tableFilters(),
        holidayTableFields.table()
      )
    );

    if (query.advanced() != null) {
      var a = query.advanced();
      spec = spec.and(contains("name", a.name()));
      spec = spec.and(localDateEquals("holidayDate", a.holidayDate(), false));
      spec = spec.and(inCodes("status", a.statusEnum(), StatusEnum::getCode));
    }

    return spec.and(orderByDesc("holidayDate"));
  }
}