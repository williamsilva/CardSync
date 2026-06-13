package com.cardsync.infrastructure.repository.spec;

import com.cardsync.domain.filter.SaleSummaryFilter;
import com.cardsync.domain.filter.query.ListQueryDto;
import com.cardsync.domain.filter.query.SortDto;
import com.cardsync.domain.model.SalesSummaryEntity;
import com.cardsync.infrastructure.repository.spec.advancedFilters.SaleSummaryAdvancedFields;
import com.cardsync.infrastructure.repository.spec.config.BaseSpecificationSupport;
import com.cardsync.infrastructure.repository.spec.config.DateFilterService;
import com.cardsync.infrastructure.repository.spec.config.SpecificationFactory;
import com.cardsync.infrastructure.repository.spec.config.Specs;
import com.cardsync.infrastructure.repository.spec.tableFilters.SaleSummaryTableFields;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class SaleSummarySpecs extends BaseSpecificationSupport<SalesSummaryEntity> {

  private final SpecificationFactory specificationFactory;
  private final SaleSummaryTableFields saleSummaryTableFields;
  private final SaleSummaryAdvancedFields saleSummaryAdvancedFields;

  public SaleSummarySpecs(
    DateFilterService dateFilterService,
    SpecificationFactory specificationFactory,
    SaleSummaryTableFields saleSummaryTableFields,
    SaleSummaryAdvancedFields saleSummaryAdvancedFields
  ) {
    super(dateFilterService);
    this.specificationFactory = specificationFactory;
    this.saleSummaryTableFields = saleSummaryTableFields;
    this.saleSummaryAdvancedFields = saleSummaryAdvancedFields;
  }

  public Specification<SalesSummaryEntity> fromQuery(ListQueryDto<SaleSummaryFilter> query) {
    Specification<SalesSummaryEntity> spec = baseFilters(query)
      .and(fetchListAssociations());

    return spec.and(orderByTableSort(query == null ? null : query.sort()));
  }

  public Specification<SalesSummaryEntity> fromQueryForTotals(ListQueryDto<SaleSummaryFilter> query) {
    return baseFilters(query);
  }

  private Specification<SalesSummaryEntity> baseFilters(ListQueryDto<SaleSummaryFilter> query) {
    Specification<SalesSummaryEntity> spec = Specs.all();

    if (query != null) {
      spec = spec.and(
        specificationFactory.fromTableFilters(
          query.tableFilters(),
          saleSummaryTableFields.table()
        )
      );

      spec = spec.and(saleSummaryAdvancedFields.advanced(query.advanced()));
    }

    return spec;
  }

  private Specification<SalesSummaryEntity> fetchListAssociations() {
    return (root, query, cb) -> {
      if (!isCountQuery(query)) {
        fetchIfNotFetched(root, "flag");
        fetchIfNotFetched(root, "company");
        fetchIfNotFetched(root, "acquirer");

        // distinct apenas na query de dados
        query.distinct(true);
      }

      return cb.conjunction();
    };
  }

  private Specification<SalesSummaryEntity> orderByTableSort(List<SortDto> sort) {
    return tableSort(sort, "pvNumber", Map.of(
      "conciliationDate", sortField("saleReconciliationDate"),
      "company",          sortJoin("company", "fantasyName"),
      "acquirer",         sortJoin("acquirer", "fantasyName"),
      "flag",             sortJoin("flag", "name"),
      "adjustmentValue",  sortJoin("adjustment", "adjustmentValue")
    ));
  }
}
