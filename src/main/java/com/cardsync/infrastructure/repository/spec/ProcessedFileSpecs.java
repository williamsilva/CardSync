package com.cardsync.infrastructure.repository.spec;

import com.cardsync.domain.filter.ProcessedFileFilter;
import com.cardsync.domain.filter.query.ListQueryDto;
import com.cardsync.domain.filter.query.SortDto;
import com.cardsync.domain.model.ProcessedFileEntity;
import com.cardsync.infrastructure.repository.spec.advancedFilters.ProcessedFileAdvancedFields;
import com.cardsync.infrastructure.repository.spec.config.BaseSpecificationSupport;
import com.cardsync.infrastructure.repository.spec.config.DateFilterService;
import com.cardsync.infrastructure.repository.spec.config.SpecificationFactory;
import com.cardsync.infrastructure.repository.spec.config.Specs;
import com.cardsync.infrastructure.repository.spec.tableFilters.ProcessedFileTableFields;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class ProcessedFileSpecs extends BaseSpecificationSupport<ProcessedFileEntity> {

  private final SpecificationFactory specificationFactory;
  private final ProcessedFileTableFields processedFileTableFields;
  private final ProcessedFileAdvancedFields processedFileAdvancedFields;

  public ProcessedFileSpecs(
    DateFilterService dateFilterService,
    SpecificationFactory specificationFactory,
    ProcessedFileTableFields processedFileTableFields,
    ProcessedFileAdvancedFields processedFileAdvancedFields
  ) {
    super(dateFilterService);
    this.specificationFactory = specificationFactory;
    this.processedFileTableFields = processedFileTableFields;
    this.processedFileAdvancedFields = processedFileAdvancedFields;
  }

  public Specification<ProcessedFileEntity> fromQuery(ListQueryDto<ProcessedFileFilter> query) {
    Specification<ProcessedFileEntity> spec = baseFilters(query)
      .and(fetchListAssociations());

    return spec.and(orderByTableSort(query == null ? null : query.sort()));
  }

  public Specification<ProcessedFileEntity> fromQueryForTotals(ListQueryDto<ProcessedFileFilter> query) {
    return baseFilters(query);
  }

  private Specification<ProcessedFileEntity> baseFilters(ListQueryDto<ProcessedFileFilter> query) {
    Specification<ProcessedFileEntity> spec = Specs.all();

    if (query != null) {
      spec = spec.and(
        specificationFactory.fromTableFilters(
          query.tableFilters(),
          processedFileTableFields.table()
        )
      );

      spec = spec.and(processedFileAdvancedFields.advanced(query.advanced()));
    }

    return spec;
  }

  private Specification<ProcessedFileEntity> fetchListAssociations() {
    return (root, query, cb) -> {
      if (!isCountQuery(query)) {
        fetchIfNotFetched(root, "originFile");
        query.distinct(true);
      }
      return cb.conjunction();
    };
  }

  private Specification<ProcessedFileEntity> orderByTableSort(List<SortDto> sort) {
    // Ordenação padrão: mais recentes primeiro (data de importação desc).
    return tableSort(sort, "dateImport", Map.of(
      "origin", sortJoin("originFile", "code")
    ));
  }
}