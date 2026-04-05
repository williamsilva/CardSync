package com.cardsync.infrastructure.repository.spec;

import com.cardsync.domain.filter.AcquirerFilter;
import com.cardsync.domain.filter.query.ListQueryDto;
import com.cardsync.domain.filter.spec.AcquirerAllowedFields;
import com.cardsync.domain.model.AcquirerEntity;
import com.cardsync.domain.model.enums.StatusEnum;
import com.cardsync.infrastructure.repository.spec.config.BaseSpecificationSupport;
import com.cardsync.infrastructure.repository.spec.config.DateFilterService;
import com.cardsync.infrastructure.repository.spec.config.SpecificationFactory;
import com.cardsync.infrastructure.repository.spec.config.Specs;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class AcquirerSpecs extends BaseSpecificationSupport<AcquirerEntity> {

  private final SpecificationFactory specificationFactory;
  private final AcquirerAllowedFields acquirerAllowedFields;

  public AcquirerSpecs(
    DateFilterService dateFilterService,
    SpecificationFactory specificationFactory,
    AcquirerAllowedFields acquirerAllowedFields
  ) {
    super(dateFilterService);
    this.specificationFactory = specificationFactory;
    this.acquirerAllowedFields = acquirerAllowedFields;
  }

  public Specification<AcquirerEntity> fromQuery(ListQueryDto<AcquirerFilter> query) {
    Specification<AcquirerEntity> spec = Specs.all();

    spec = spec.and(
      specificationFactory.fromTableFilters(
        query.tableFilters(),
        acquirerAllowedFields.table()
      )
    );

    if (query.advanced() != null) {
      var a = query.advanced();

      spec = spec.and(contains("cnpj", a.cnpj()));
      spec = spec.and(contains("fantasyName", a.fantasyName()));
      spec = spec.and(contains("socialReason", a.socialReason()));
      spec = spec.and(rangeOdt("createdAt", a.createdAtFrom(), a.createdAtTo()));

      spec = spec.and(
        inPath(a.createdBy(), value -> {
          try {
            return UUID.fromString(value);
          } catch (Exception e) {
            return null;
          }
        }, "createdBy", "id")
      );

      spec = spec.and(inCodes("status", a.statusEnum(), StatusEnum::getCode));
    }

    if (!isBlank(query.globalFilter())) {
      String gf = query.globalFilter();

      spec = spec.and(
        anyOf(
          contains("cnpj", gf),
          contains("fantasyName", gf),
          contains("socialReason", gf),
          containsPath(gf, "createdBy", "name"),
          containsPath(gf, "createdBy", "userName")
        )
      );
    }

    return spec.and(orderByAsc("fantasyName"));
  }
}