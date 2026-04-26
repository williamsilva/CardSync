package com.cardsync.infrastructure.repository.spec;

import com.cardsync.domain.filter.ContractFilter;
import com.cardsync.domain.filter.query.ListQueryDto;
import com.cardsync.infrastructure.repository.spec.tableFilters.ContractTableFields;
import com.cardsync.domain.model.ContractEntity;
import com.cardsync.domain.model.enums.ContractEnum;
import com.cardsync.infrastructure.repository.spec.config.BaseSpecificationSupport;
import com.cardsync.infrastructure.repository.spec.config.DateFilterService;
import com.cardsync.infrastructure.repository.spec.config.SpecificationFactory;
import com.cardsync.infrastructure.repository.spec.config.Specs;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ContractSpecs extends BaseSpecificationSupport<ContractEntity> {

  private final ContractTableFields contractTableFields;
  private final SpecificationFactory specificationFactory;

  public ContractSpecs(
    DateFilterService dateFilterService,
    ContractTableFields contractTableFields,
    SpecificationFactory specificationFactory
  ) {
    super(dateFilterService);
    this.contractTableFields = contractTableFields;
    this.specificationFactory = specificationFactory;
  }

  public Specification<ContractEntity> fromQuery(ListQueryDto<ContractFilter> query) {
    Specification<ContractEntity> spec = Specs.all();

    spec = spec.and(
      specificationFactory.fromTableFilters(
        query.tableFilters(),
        contractTableFields.table()
      )
    );

    if (query.advanced() != null) {
      var a = query.advanced();

      spec = spec.and(inCodes("status", a.contractEnum(), ContractEnum::getCode));
      spec = spec.and(datePeriod("endDate", a.periodEndDate(), a.endDate(), true));
      spec = spec.and(datePeriod("startDate", a.periodStartDate(), a.startDate(), true));

      spec = spec.and(
        inPath(a.company(), value -> {
          try {
            return UUID.fromString(value);
          } catch (Exception e) {
            return null;
          }
        }, "company", "id")
      );

      spec = spec.and(
        inPath(a.acquirer(), value -> {
          try {
            return UUID.fromString(value);
          } catch (Exception e) {
            return null;
          }
        }, "acquirer", "id")
      );

      spec = spec.and(
        inPath(a.createdBy(), value -> {
          try {
            return UUID.fromString(value);
          } catch (Exception e) {
            return null;
          }
        }, "createdBy", "id")
      );

      spec = spec.and(
        inPath(a.establishment(), value -> {
          try {
            return UUID.fromString(value);
          } catch (Exception e) {
            return null;
          }
        }, "establishment", "id")
      );

    }

    if (!isBlank(query.globalFilter())) {
      String gf = query.globalFilter();
      spec = spec.and(anyOf(
        contains("description", gf),
        containsPath(gf, "company", "fantasyName"),
        containsPath(gf, "acquirer", "fantasyName")
      ));
    }

    return spec.and(orderByAsc("description"));
  }

}