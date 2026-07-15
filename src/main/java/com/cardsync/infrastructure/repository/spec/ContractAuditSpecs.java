package com.cardsync.infrastructure.repository.spec;

import com.cardsync.domain.filter.ContractAuditModelFilter;
import com.cardsync.domain.filter.query.ListQueryDto;
import com.cardsync.domain.filter.query.SortDto;
import com.cardsync.domain.model.ContractAuditEntity;
import com.cardsync.domain.model.enums.ModalityEnum;
import com.cardsync.domain.model.enums.StatusTransactionReasonEnum;
import com.cardsync.infrastructure.repository.spec.advancedFilters.ContractAuditAdvancedFields;
import com.cardsync.infrastructure.repository.spec.config.BaseSpecificationSupport;
import com.cardsync.infrastructure.repository.spec.config.DateFilterService;
import com.cardsync.infrastructure.repository.spec.config.SpecificationFactory;
import com.cardsync.infrastructure.repository.spec.config.Specs;
import com.cardsync.infrastructure.repository.spec.tableFilters.ContractAuditTableFields;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class ContractAuditSpecs extends BaseSpecificationSupport<ContractAuditEntity> {

  private final SpecificationFactory specificationFactory;
  private final ContractAuditTableFields conciliationWaitingTableFields;
  private final ContractAuditAdvancedFields conciliationWaitingAdvancedFields;

  public ContractAuditSpecs(
    DateFilterService dateFilterService,
    SpecificationFactory specificationFactory,
    ContractAuditTableFields conciliationWaitingTableFields,
    ContractAuditAdvancedFields conciliationWaitingAdvancedFields
  ) {
    super(dateFilterService);
    this.specificationFactory = specificationFactory;
    this.conciliationWaitingTableFields = conciliationWaitingTableFields;
    this.conciliationWaitingAdvancedFields = conciliationWaitingAdvancedFields;
  }

  public Specification<ContractAuditEntity> fromQuery(ListQueryDto<ContractAuditModelFilter> query) {
    Specification<ContractAuditEntity> spec = baseFilters(query)
      .and(fetchListAssociations());

    return spec.and(orderByTableSort(query == null ? null : query.sort()));
  }

  public Specification<ContractAuditEntity> fromQueryForTotals(ListQueryDto<ContractAuditModelFilter> query) {
    return baseFilters(query);
  }

  private Specification<ContractAuditEntity> baseFilters(ListQueryDto<ContractAuditModelFilter> query) {
    Specification<ContractAuditEntity> spec = Specs.all();

    if (query != null) {
      spec = spec.and(
        specificationFactory.fromTableFilters(
          query.tableFilters(),
          conciliationWaitingTableFields.table()
        )
      );

      spec = spec.and(conciliationWaitingAdvancedFields.advanced(query.advanced()));

      if (!isBlank(query.globalFilter())) {
        String gf = query.globalFilter();

        spec = spec.and(
          anyOf(
            numberFilter(gf, "nsu"),
            startsWith(gf, "authorization")
          )
        );
      }
    }

    return spec;
  }

  private Specification<ContractAuditEntity> fetchListAssociations() {
    return (root, query, cb) -> {
      if (!isCountQuery(query)) {
        fetchIfNotFetched(root, "flag");
        fetchIfNotFetched(root, "company");
        fetchIfNotFetched(root, "acquirer");
        fetchIfNotFetched(root, "establishment");
        fetchIfNotFetched(root, "transactionAcq");
        fetchIfNotFetched(root, "transactionErp");

        // distinct apenas na query de dados
        query.distinct(true);
      }

      return cb.conjunction();
    };
  }

  private Specification<ContractAuditEntity> orderByTableSort(List<SortDto> sort) {
    return tableSort(sort, "nsu", Map.of(
      "conciliationDate",  sortField("saleReconciliationDate"),
      "company",           sortJoin("company", "fantasyName"),
      "establishment",     sortJoin("establishment", "pvNumber"),
      "acquirer",          sortJoin("acquirer", "fantasyName"),
      "flag",              sortJoin("flag", "name"),
      "adjustmentValue",   sortJoin("adjustment", "adjustmentValue")
    ));
  }
}