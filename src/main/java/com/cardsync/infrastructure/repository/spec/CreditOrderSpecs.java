package com.cardsync.infrastructure.repository.spec;

import com.cardsync.core.config.ImplantationDateProvider;
import com.cardsync.domain.filter.CreditOrderFilter;
import com.cardsync.domain.filter.query.ListQueryDto;
import com.cardsync.domain.filter.query.SortDto;
import com.cardsync.domain.model.CreditOrderEntity;
import com.cardsync.infrastructure.repository.spec.advancedFilters.CreditOrderAdvancedFields;
import com.cardsync.infrastructure.repository.spec.config.BaseSpecificationSupport;
import com.cardsync.infrastructure.repository.spec.config.DateFilterService;
import com.cardsync.infrastructure.repository.spec.config.SpecificationFactory;
import com.cardsync.infrastructure.repository.spec.config.Specs;
import com.cardsync.infrastructure.repository.spec.tableFilters.CreditOrderTableFields;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class CreditOrderSpecs extends BaseSpecificationSupport<CreditOrderEntity> {

  private final ImplantationDateProvider implantationDateProvider;
  private final SpecificationFactory specificationFactory;
  private final CreditOrderTableFields creditOrderTableFields;
  private final CreditOrderAdvancedFields creditOrderAdvancedFields;

  public CreditOrderSpecs(
    ImplantationDateProvider implantationDateProvider,
    DateFilterService dateFilterService,
    SpecificationFactory specificationFactory,
    CreditOrderTableFields creditOrderTableFields,
    CreditOrderAdvancedFields creditOrderAdvancedFields
  ) {
    super(dateFilterService);
    this.implantationDateProvider = implantationDateProvider;
    this.specificationFactory = specificationFactory;
    this.creditOrderTableFields = creditOrderTableFields;
    this.creditOrderAdvancedFields = creditOrderAdvancedFields;
  }

  public Specification<CreditOrderEntity> fromQuery(ListQueryDto<CreditOrderFilter> query) {
    Specification<CreditOrderEntity> spec = baseFilters(query)
      .and(fetchListAssociations());

    return spec.and(orderByTableSort(query == null ? null : query.sort()));
  }

  public Specification<CreditOrderEntity> fromQueryForTotals(ListQueryDto<CreditOrderFilter> query) {
    return baseFilters(query);
  }

  private Specification<CreditOrderEntity> baseFilters(ListQueryDto<CreditOrderFilter> query) {
    Specification<CreditOrderEntity> spec = Specs.all();

    if (query != null) {
      spec = spec.and(
        specificationFactory.fromTableFilters(
          query.tableFilters(),
          creditOrderTableFields.table()
        )
      );

      spec = spec.and(creditOrderAdvancedFields.advanced(query.advanced()));

      spec = spec.and(dateGreaterThanOrEqual("rvDate", implantationDateProvider.get(), false));
    }

    return spec;
  }

  private Specification<CreditOrderEntity> fetchListAssociations() {
    return (root, query, cb) -> {
      if (!isCountQuery(query)) {
        fetchIfNotFetched(root, "flag");
        fetchIfNotFetched(root, "company");
        fetchIfNotFetched(root, "acquirer");
        fetchIfNotFetched(root, "processedFile");

        var bankingDomicile = fetchIfNotFetched(root, "bankingDomicile");
        fetchIfNotFetched(bankingDomicile, "bank");

        var salesSummary = fetchIfNotFetched(root, "salesSummary");
        var salesSummaryBankingDomicile = fetchIfNotFetched(salesSummary, "bankingDomicile");
        fetchIfNotFetched(salesSummaryBankingDomicile, "bank");

        // distinct apenas na query de dados
        query.distinct(true);
      }

      return cb.conjunction();
    };
  }

  private Specification<CreditOrderEntity> orderByTableSort(List<SortDto> sort) {
    return tableSort(sort, "rvDate", Map.of(
      "conciliationDate",  sortField("saleReconciliationDate"),
      "company",           sortJoin("company", "fantasyName"),
      "establishment",     sortJoin("establishment", "pvNumber"),
      "acquirer",          sortJoin("acquirer", "fantasyName"),
      "flag",              sortJoin("flag", "name"),
      "adjustmentValue",   sortJoin("adjustment", "adjustmentValue")
    ));
  }
}