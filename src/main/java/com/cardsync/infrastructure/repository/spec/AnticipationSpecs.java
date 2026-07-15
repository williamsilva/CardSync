package com.cardsync.infrastructure.repository.spec;

import com.cardsync.domain.filter.AnticipationFilter;
import com.cardsync.domain.filter.query.ListQueryDto;
import com.cardsync.domain.filter.query.SortDto;
import com.cardsync.domain.model.AnticipationEntity;
import com.cardsync.infrastructure.repository.spec.advancedFilters.AnticipationAdvancedFields;
import com.cardsync.infrastructure.repository.spec.config.BaseSpecificationSupport;
import com.cardsync.infrastructure.repository.spec.config.DateFilterService;
import com.cardsync.infrastructure.repository.spec.config.SpecificationFactory;
import com.cardsync.infrastructure.repository.spec.config.Specs;
import com.cardsync.infrastructure.repository.spec.tableFilters.AnticipationTableFields;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class AnticipationSpecs extends BaseSpecificationSupport<AnticipationEntity> {

  private final SpecificationFactory specificationFactory;
  private final AnticipationTableFields anticipationTableFields;
  private final AnticipationAdvancedFields anticipationAdvancedFields;

  public AnticipationSpecs(
    DateFilterService dateFilterService,
    SpecificationFactory specificationFactory,
    AnticipationTableFields anticipationTableFields,
    AnticipationAdvancedFields anticipationAdvancedFields
  ) {
    super(dateFilterService);
    this.specificationFactory = specificationFactory;
    this.anticipationTableFields = anticipationTableFields;
    this.anticipationAdvancedFields = anticipationAdvancedFields;
  }

  public Specification<AnticipationEntity> fromQuery(ListQueryDto<AnticipationFilter> query) {
    Specification<AnticipationEntity> spec = baseFilters(query)
      .and(fetchListAssociations());

    return spec.and(orderByTableSort(query == null ? null : query.sort()));
  }

  public Specification<AnticipationEntity> fromQueryForTotals(ListQueryDto<AnticipationFilter> query) {
    return baseFilters(query);
  }

  private Specification<AnticipationEntity> baseFilters(ListQueryDto<AnticipationFilter> query) {
    Specification<AnticipationEntity> spec = Specs.all();

    if (query != null) {
      spec = spec.and(
        specificationFactory.fromTableFilters(
          query.tableFilters(),
          anticipationTableFields.table()
        )
      );

      spec = spec.and(anticipationAdvancedFields.advanced(query.advanced()));
    }

    return spec;
  }

  private Specification<AnticipationEntity> fetchListAssociations() {
    return (root, query, cb) -> {
      if (!isCountQuery(query)) {
        fetchIfNotFetched(root, "flag");
        fetchIfNotFetched(root, "company");
        fetchIfNotFetched(root, "acquirer");
        fetchIfNotFetched(root, "establishment");
        fetchIfNotFetched(root, "processedFile");
        fetchIfNotFetched(root, "salesSummary");

        var bankingDomicile = fetchIfNotFetched(root, "bankingDomicile");
        fetchIfNotFetched(bankingDomicile, "bank");

        // distinct apenas na query de dados
        query.distinct(true);
      }

      return cb.conjunction();
    };
  }

  private Specification<AnticipationEntity> orderByTableSort(List<SortDto> sort) {
    return tableSort(sort, "pvNumber", Map.of(
      "conciliationDate",  sortField("saleReconciliationDate"),
      "company",           sortJoin("company", "fantasyName"),
      "establishment",     sortJoin("establishment", "pvNumber"),
      "acquirer",          sortJoin("acquirer", "fantasyName"),
      "flag",              sortJoin("flag", "name"),
      "adjustmentValue",   sortJoin("adjustment", "adjustmentValue")
    ));
  }
}