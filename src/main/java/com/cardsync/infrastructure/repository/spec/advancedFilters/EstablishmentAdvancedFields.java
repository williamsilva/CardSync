package com.cardsync.infrastructure.repository.spec.advancedFilters;

import com.cardsync.domain.filter.EstablishmentFilter;
import com.cardsync.domain.model.EstablishmentEntity;
import com.cardsync.domain.model.enums.*;
import com.cardsync.infrastructure.repository.spec.config.BaseSpecificationSupport;
import com.cardsync.infrastructure.repository.spec.config.DateFilterService;
import com.cardsync.infrastructure.repository.spec.config.Specs;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

@Component
public class EstablishmentAdvancedFields extends BaseSpecificationSupport<EstablishmentEntity> {

  public EstablishmentAdvancedFields(DateFilterService dateFilterService) {
    super(dateFilterService);
  }

  public Specification<EstablishmentEntity> advanced(EstablishmentFilter filter) {
    Specification<EstablishmentEntity> spec = Specs.all();
    if (filter == null) {
      return spec;
    }

    // pvNumber é numérico: contains() faz LOWER(coluna), que quebra no Postgres (funcionava
    // no MySQL por cast implícito). numberFilter faz igualdade quando o valor é numérico.
    spec = spec.and(numberFilter(filter.pvNumber(), "pvNumber"));

    spec = spec.and(inCodes("status", filter.statusEnum(), StatusEnum::getCode));
    spec = spec.and(inCodes("type", filter.typeEnum(), TypeEstablishmentEnum::getCode));
    spec = spec.and(offsetDateTimePeriod("createdAt", filter.periodCreatedAt(), filter.createdAt(), true));

    spec = spec.and(inPath(filter.company(), EstablishmentAdvancedFields::parseUuidOrNull,"company", "id"));
    spec = spec.and(inPath(filter.acquirer(), EstablishmentAdvancedFields::parseUuidOrNull,"acquirer", "id"));
    spec = spec.and(inPath(filter.createdBy(), EstablishmentAdvancedFields::parseUuidOrNull,"createdBy"));

    return spec;
  }

}
