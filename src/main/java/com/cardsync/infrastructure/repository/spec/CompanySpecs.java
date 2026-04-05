package com.cardsync.infrastructure.repository.spec;

import com.cardsync.domain.filter.CompanyFilter;
import com.cardsync.domain.filter.query.ListQueryDto;
import com.cardsync.domain.filter.spec.CompanyAllowedFields;
import com.cardsync.domain.model.CompanyEntity;
import com.cardsync.domain.model.enums.StatusEnum;
import com.cardsync.domain.model.enums.TypeCompanyEnum;
import com.cardsync.infrastructure.repository.spec.config.BaseSpecificationSupport;
import com.cardsync.infrastructure.repository.spec.config.DateFilterService;
import com.cardsync.infrastructure.repository.spec.config.SpecificationFactory;
import com.cardsync.infrastructure.repository.spec.config.Specs;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class CompanySpecs extends BaseSpecificationSupport<CompanyEntity> {

  private final SpecificationFactory specificationFactory;
  private final CompanyAllowedFields companyAllowedFields;

  public CompanySpecs(
    DateFilterService dateFilterService,
    SpecificationFactory specificationFactory,
    CompanyAllowedFields companyAllowedFields
  ) {
    super(dateFilterService);
    this.specificationFactory = specificationFactory;
    this.companyAllowedFields = companyAllowedFields;
  }

  public Specification<CompanyEntity> fromQuery(ListQueryDto<CompanyFilter> query) {
    Specification<CompanyEntity> spec = Specs.all();

    spec = spec.and(
      specificationFactory.fromTableFilters(
        query.tableFilters(),
        companyAllowedFields.table()
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
      spec = spec.and(inCodes("type", a.typeEnum(), TypeCompanyEnum::getCode));
    }

    if (!isBlank(query.globalFilter())) {
      String gf = query.globalFilter();

      spec = spec.and(
        anyOf(
          contains("cnpj", gf)
        )
      );
    }

    return spec.and(orderByAsc("fantasyName"));
  }
}