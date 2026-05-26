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
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

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
            contains(gf, "nsu"),
            contains(gf, "authorization")
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
      }

      return cb.conjunction();
    };
  }

  private Specification<ContractAuditEntity> orderByTableSort(List<SortDto> sort) {
    return (root, query, cb) -> {
      if (isCountQuery(query)) {
        return cb.conjunction();
      }

      List<Order> orders = new ArrayList<>();

      if (sort != null) {
        for (SortDto item : sort) {
          if (item == null || item.field() == null || item.field().isBlank() || item.order() == null) {
            continue;
          }

          boolean ascending = item.order() == 1;
          Expression<?> expression = sortExpression(root, query, cb, item.field().trim(), !ascending);

          if (expression == null) {
            continue;
          }

          orders.add(ascending ? cb.asc(expression) : cb.desc(expression));
        }
      }

      if (orders.isEmpty()) {
        orders.add(cb.desc(root.get("nsu")));
      }

      orders.add(cb.desc(root.get("id")));
      query.orderBy(orders);

      return cb.conjunction();
    };
  }

  private Expression<?> sortExpression(Root<ContractAuditEntity> root, CriteriaQuery<?> query,
                                       CriteriaBuilder cb, String field, boolean descending) {
    return switch (field) {
      case "saleDate" -> root.get("saleDate");
      case "conciliationDate" -> root.get("saleReconciliationDate");


      case "company" -> join(root, "company").get("fantasyName");
      case "establishment" -> join(root, "establishment").get("pvNumber");
      case "acquirer" -> join(root, "acquirer").get("fantasyName");
      case "flag" -> join(root, "flag").get("name");
      case "adjustmentValue" -> join(root, "adjustment").get("adjustmentValue");

      default -> directRootPathOrNull(root, field);
    };
  }

  private Path<?> directRootPathOrNull(Root<ContractAuditEntity> root, String field) {
    try {
      return root.get(field);
    } catch (IllegalArgumentException ex) {
      return null;
    }
  }
}
