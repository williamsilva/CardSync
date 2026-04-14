package com.cardsync.infrastructure.repository.spec;

import com.cardsync.domain.filter.ContractFilter;
import com.cardsync.domain.filter.query.ListQueryDto;
import com.cardsync.domain.filter.spec.ContractAllowedFields;
import com.cardsync.domain.model.ContractEntity;
import com.cardsync.infrastructure.repository.spec.config.BaseSpecificationSupport;
import com.cardsync.infrastructure.repository.spec.config.DateFilterService;
import com.cardsync.infrastructure.repository.spec.config.SpecificationFactory;
import com.cardsync.infrastructure.repository.spec.config.Specs;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ContractSpecs extends BaseSpecificationSupport<ContractEntity> {

  private final SpecificationFactory specificationFactory;
  private final ContractAllowedFields contractAllowedFields;

  public ContractSpecs(
    DateFilterService dateFilterService,
    SpecificationFactory specificationFactory,
    ContractAllowedFields contractAllowedFields
  ) {
    super(dateFilterService);
    this.specificationFactory = specificationFactory;
    this.contractAllowedFields = contractAllowedFields;
  }

  public Specification<ContractEntity> fromQuery(ListQueryDto<ContractFilter> query) {
    Specification<ContractEntity> spec = Specs.all();

    spec = spec.and(
      specificationFactory.fromTableFilters(
        query.tableFilters(),
        contractAllowedFields.table()
      )
    );

    if (query.advanced() != null) {
      var a = query.advanced();

      /* spec = spec.and(contains("cnpj", a.cnpj()));
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
      */
    }
    return spec.and(orderByAsc("id"));
  }
}