package com.cardsync.infrastructure.repository.spec;

import com.cardsync.domain.filter.BankFilter;
import com.cardsync.domain.filter.query.ListQueryDto;
import com.cardsync.domain.model.BankEntity;
import com.cardsync.domain.model.enums.StatusEnum;
import com.cardsync.infrastructure.repository.spec.config.BaseSpecificationSupport;
import com.cardsync.infrastructure.repository.spec.config.DateFilterService;
import com.cardsync.infrastructure.repository.spec.config.SpecificationFactory;
import com.cardsync.infrastructure.repository.spec.config.Specs;
import com.cardsync.infrastructure.repository.spec.tableFilters.BankTableFields;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

@Component
public class BankSpecs extends BaseSpecificationSupport<BankEntity> {

  private final BankTableFields bankTableFields;
  private final SpecificationFactory specificationFactory;

  public BankSpecs(
    DateFilterService dateFilterService,
    SpecificationFactory specificationFactory,
    BankTableFields bankTableFields
  ) {
    super(dateFilterService);
    this.specificationFactory = specificationFactory;
    this.bankTableFields = bankTableFields;
  }

  public Specification<BankEntity> fromQuery(ListQueryDto<BankFilter> query) {
    Specification<BankEntity> spec = Specs.all();

    spec = spec.and(
      specificationFactory.fromTableFilters(
        query.tableFilters(),
        bankTableFields.table()
      )
    );

    if (query.advanced() != null) {
      var a = query.advanced();
      spec = spec.and(contains("code", a.code()));
      spec = spec.and(contains("name", a.name()));
      spec = spec.and(contains("ispb", a.ispb()));
      spec = spec.and(inCodes("status", a.statusEnum(), StatusEnum::getCode));
    }

    return spec.and(orderByAsc("name"));
  }
}