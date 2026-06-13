package com.cardsync.infrastructure.repository.spec.advancedFilters;

import com.cardsync.domain.filter.ProcessedFileFilter;
import com.cardsync.domain.model.ProcessedFileEntity;
import com.cardsync.infrastructure.repository.spec.config.BaseSpecificationSupport;
import com.cardsync.infrastructure.repository.spec.config.DateFilterService;
import com.cardsync.infrastructure.repository.spec.config.Specs;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.function.Function;

@Component
public class ProcessedFileAdvancedFields extends BaseSpecificationSupport<ProcessedFileEntity> {

  public ProcessedFileAdvancedFields(DateFilterService dateFilterService) {
    super(dateFilterService);
  }

  public Specification<ProcessedFileEntity> advanced(ProcessedFileFilter filter) {
    if (filter == null) {
      return Specs.all();
    }

    Specification<ProcessedFileEntity> spec = Specs.all();

    spec = spec.and(contains(filter.fileName(), "file"));
    spec = spec.and(contains(filter.typeFile(), "typeFile"));
    spec = spec.and(contains(filter.commercialName(), "commercialName"));

    // Enums STRING: comparação enum-a-enum (identity), pois a coluna é @Enumerated(STRING)
    spec = spec.and(inCodes("status", filter.status(), Function.identity()));
    spec = spec.and(inCodes("group", filter.group(), Function.identity()));

    spec = spec.and(inPath(
      filter.origins(),
      ProcessedFileAdvancedFields::parseUuidOrNull,
      "originFile",
      "id"
    ));

    spec = spec.and(offsetDateTimePeriod(
      "dateImport",
      filter.periodDateImport(),
      filter.dateImport(),
      true
    ));

    spec = spec.and(localDatePeriod(
      "dateFile",
      filter.periodDateFile(),
      filter.dateFile(),
      true
    ));

    return spec;
  }
}