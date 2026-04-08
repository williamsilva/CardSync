package com.cardsync.infrastructure.repository.spec;

import com.cardsync.domain.filter.EstablishmentFilter;
import com.cardsync.domain.filter.query.ListQueryDto;
import com.cardsync.domain.filter.spec.EstablishmentAllowedFields;
import com.cardsync.domain.model.EstablishmentEntity;
import com.cardsync.domain.model.enums.StatusEnum;
import com.cardsync.domain.model.enums.TypeEstablishmentEnum;
import com.cardsync.infrastructure.repository.spec.config.BaseSpecificationSupport;
import com.cardsync.infrastructure.repository.spec.config.DateFilterService;
import com.cardsync.infrastructure.repository.spec.config.SpecificationFactory;
import com.cardsync.infrastructure.repository.spec.config.Specs;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class EstablishmentSpecs extends BaseSpecificationSupport<EstablishmentEntity> {

  private final SpecificationFactory specificationFactory;
  private final EstablishmentAllowedFields establishmentAllowedFields;

  public EstablishmentSpecs(
    DateFilterService dateFilterService,
    SpecificationFactory specificationFactory,
    EstablishmentAllowedFields establishmentAllowedFields
  ) {
    super(dateFilterService);
    this.specificationFactory = specificationFactory;
    this.establishmentAllowedFields = establishmentAllowedFields;
  }

  public Specification<EstablishmentEntity> fromQuery(ListQueryDto<EstablishmentFilter> query) {
    Specification<EstablishmentEntity> spec = Specs.all();

    spec = spec.and(
      specificationFactory.fromTableFilters(
        query.tableFilters(),
        establishmentAllowedFields.table()
      )
    );

    if (query.advanced() != null) {
      var a = query.advanced();

      spec = spec.and(contains("pvNumber", a.pvNumber()));
      spec = spec.and(inCodes("status", a.statusEnum(), StatusEnum::getCode));
      spec = spec.and(rangeOdt("createdAt", a.createdAtFrom(), a.createdAtTo()));
      spec = spec.and(inCodes("type", a.typeEnum(), TypeEstablishmentEnum::getCode));

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

    }

    if (!isBlank(query.globalFilter())) {
      String gf = query.globalFilter();

      spec = spec.and(
        anyOf(
          containsPath(gf, "createdBy", "name")
        )
      );
    }

    return spec.and(orderByAscPath("company", "fantasyName"));
  }
}