package com.cardsync.infrastructure.repository.spec;

import com.cardsync.domain.filter.BankFilter;
import com.cardsync.domain.filter.query.ListQueryDto;
import com.cardsync.domain.filter.query.SortDto;
import com.cardsync.domain.model.BankEntity;
import com.cardsync.domain.model.enums.StatusEnum;
import com.cardsync.infrastructure.repository.spec.config.BaseSpecificationSupport;
import com.cardsync.infrastructure.repository.spec.config.DateFilterService;
import com.cardsync.infrastructure.repository.spec.config.SpecificationFactory;
import com.cardsync.infrastructure.repository.spec.config.Specs;
import com.cardsync.infrastructure.repository.spec.tableFilters.BankTableFields;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class BankSpecs extends BaseSpecificationSupport<BankEntity> {

  private final BankTableFields bankTableFields;
  private final SpecificationFactory specificationFactory;

  public BankSpecs(
    BankTableFields bankTableFields,
    DateFilterService dateFilterService,
    SpecificationFactory specificationFactory
  ) {
    super(dateFilterService);
    this.bankTableFields = bankTableFields;
    this.specificationFactory = specificationFactory;
  }

  public Specification<BankEntity> fromQuery(ListQueryDto<BankFilter> query) {
    Specification<BankEntity> spec = baseFilters(query)
      .and(fetchListAssociations());

    return spec.and(orderByTableSort(query == null ? null : query.sort()));
  }

  public Specification<BankEntity> fromQueryForTotals(ListQueryDto<BankFilter> query) {
    return baseFilters(query);
  }

  private Specification<BankEntity> baseFilters(ListQueryDto<BankFilter> query) {
    Specification<BankEntity> spec = Specs.all();

    if (query != null) {
      spec = spec.and(
        specificationFactory.fromTableFilters(
          query.tableFilters(),
          bankTableFields.table()
        )
      );

    }

    return spec.and(orderByAsc("name"));
  }

  private Specification<BankEntity> orderByTableSort(List<SortDto> sort) {
    return tableSort(sort, "name", Map.of(
      // "conciliationDate",  sortField("saleReconciliationDate")
    ));
  }

  private Specification<BankEntity> fetchListAssociations() {
    return (root, query, cb) -> {
      if (!isCountQuery(query)) {

        // distinct apenas na query de dados
        query.distinct(true);
      }

      return cb.conjunction();
    };
  }
}