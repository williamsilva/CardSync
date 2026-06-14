package com.cardsync.infrastructure.repository.spec.advancedFilters;

import com.cardsync.domain.filter.NoFileDayFilter;
import com.cardsync.domain.model.NoFileDayEntity;
import com.cardsync.infrastructure.repository.spec.config.BaseSpecificationSupport;
import com.cardsync.infrastructure.repository.spec.config.DateFilterService;
import com.cardsync.infrastructure.repository.spec.config.Specs;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

@Component
public class NoFileDayAdvancedFields extends BaseSpecificationSupport<NoFileDayEntity> {

  public NoFileDayAdvancedFields(DateFilterService dateFilterService) {
    super(dateFilterService);
  }

  public Specification<NoFileDayEntity> advanced(NoFileDayFilter filter) {
    if (filter == null) {
      return Specs.all();
    }

    Specification<NoFileDayEntity> spec = Specs.all();

    // Buscas por texto (startsWith para aproveitar índices)
    /*
    spec = spec.and(contains("description", a.description()));
      spec = spec.and(localDateEquals("noFileDate", a.noFileDate(), false));
      spec = spec.and(inCodes("fileGroup", a.fileGroup(), FileGroupEnum::name));
      spec = spec.and(inCodes("dayType", a.dayType(), NoFileDayTypeEnum::getCode));
      spec = spec.and(inCodes("status", a.statusEnum(), StatusEnum::getCode));
     */

    return spec;
  }
}