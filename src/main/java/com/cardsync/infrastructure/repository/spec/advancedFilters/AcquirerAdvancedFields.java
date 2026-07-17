package com.cardsync.infrastructure.repository.spec.advancedFilters;

import com.cardsync.domain.filter.AcquirerFilter;
import com.cardsync.domain.model.AcquirerEntity;
import com.cardsync.domain.model.enums.StatusEnum;
import com.cardsync.infrastructure.repository.spec.config.BaseSpecificationSupport;
import com.cardsync.infrastructure.repository.spec.config.DateFilterService;
import com.cardsync.infrastructure.repository.spec.config.Specs;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

@Component
public class AcquirerAdvancedFields extends BaseSpecificationSupport<AcquirerEntity> {

  public AcquirerAdvancedFields(DateFilterService dateFilterService) {
    super(dateFilterService);
  }

  public Specification<AcquirerEntity> advanced(AcquirerFilter filter) {
    if (filter == null) {
      return Specs.all();
    }

    Specification<AcquirerEntity> spec = Specs.all();

    spec = spec.and(contains("cnpj", filter.cnpj()));
    spec = spec.and(contains("fantasyName", filter.fantasyName()));
    spec = spec.and(contains("socialReason", filter.socialReason()));
    spec = spec.and(rangeOdt("createdAt", filter.createdAtFrom(), filter.createdAtTo()));

    spec = spec.and(inCodes("status", filter.statusEnum(), StatusEnum::getCode));

    spec = spec.and(inPath(filter.createdBy(), TransactionAcqAdvancedFields::parseUuidOrNull,"createdBy", "id"));

    return spec;
  }

}