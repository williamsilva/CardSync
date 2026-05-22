package com.cardsync.infrastructure.repository.spec;

import com.cardsync.domain.filter.ConciliationWaitingModelFilter;
import com.cardsync.domain.filter.query.ListQueryDto;
import com.cardsync.domain.filter.query.SortDto;

import com.cardsync.domain.model.TransactionErpEntity;
import com.cardsync.domain.model.enums.ModalityEnum;
import com.cardsync.domain.model.enums.StatusTransactionEnum;
import com.cardsync.domain.model.enums.StatusTransactionReasonEnum;
import com.cardsync.infrastructure.repository.spec.advancedFilters.ConciliationWaitingErpAdvancedFields;
import com.cardsync.infrastructure.repository.spec.config.BaseSpecificationSupport;
import com.cardsync.infrastructure.repository.spec.config.DateFilterService;
import com.cardsync.infrastructure.repository.spec.config.SpecificationFactory;
import com.cardsync.infrastructure.repository.spec.config.Specs;
import com.cardsync.infrastructure.repository.spec.tableFilters.ConciliationWaitingErpTableFields;
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Component
public class ConciliationWaitingErpSpecs extends BaseSpecificationSupport<TransactionErpEntity> {

  private final SpecificationFactory specificationFactory;
  private final ConciliationWaitingErpTableFields conciliationWaitingTableFields;
  private final ConciliationWaitingErpAdvancedFields conciliationWaitingAdvancedFields;

  public ConciliationWaitingErpSpecs(
    DateFilterService dateFilterService,
    SpecificationFactory specificationFactory,
    ConciliationWaitingErpTableFields conciliationWaitingTableFields,
    ConciliationWaitingErpAdvancedFields conciliationWaitingAdvancedFields
  ) {
    super(dateFilterService);
    this.specificationFactory = specificationFactory;
    this.conciliationWaitingTableFields = conciliationWaitingTableFields;
    this.conciliationWaitingAdvancedFields = conciliationWaitingAdvancedFields;
  }

  public Specification<TransactionErpEntity> fromQuery(ListQueryDto<ConciliationWaitingModelFilter> query) {
    Specification<TransactionErpEntity> spec = baseFilters(query)
      .and(fetchListAssociations());

    return spec.and(orderByTableSort(query == null ? null : query.sort()));
  }

  public Specification<TransactionErpEntity> fromQueryForTotals(ListQueryDto<ConciliationWaitingModelFilter> query) {
    return baseFilters(query);
  }

  private Specification<TransactionErpEntity> baseFilters(ListQueryDto<ConciliationWaitingModelFilter> query) {
    Specification<TransactionErpEntity> spec = Specs.all();

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

    spec = spec.and(Specification.not(inCodes("modality", getModalityEnum(), ModalityEnum::getCode)));
    spec = spec.and(Specification.not(inCodes("statusTransaction", List.of(StatusTransactionEnum.DELETED), StatusTransactionEnum::getCode)));
    spec = spec.and(inCodes("statusTransactionReason",
      List.of(StatusTransactionReasonEnum.CV_NOT_FOUND_ADQ), StatusTransactionReasonEnum::getCode));

    return spec;
  }

  private static List<ModalityEnum> getModalityEnum() {
    return List.of(ModalityEnum.DIGITAL_WALLET);
  }

  private Specification<TransactionErpEntity> fetchListAssociations() {
    return (root, query, cb) -> {
      if (!isCountQuery(query)) {
        fetchIfNotFetched(root, "flag");
        fetchIfNotFetched(root, "company");
        fetchIfNotFetched(root, "acquirer");
        fetchIfNotFetched(root, "adjustment");
        fetchIfNotFetched(root, "processedFile");
        fetchIfNotFetched(root, "establishment");
      }

      return cb.conjunction();
    };
  }

  private Specification<TransactionErpEntity> orderByTableSort(List<SortDto> sort) {
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
        orders.add(cb.desc(root.get("saleDate")));
      }

      orders.add(cb.desc(root.get("id")));
      query.orderBy(orders);

      return cb.conjunction();
    };
  }

  private Expression<?> sortExpression(Root<TransactionErpEntity> root, CriteriaQuery<?> query,
                                       CriteriaBuilder cb, String field, boolean descending) {
    return switch (field) {
      case "saleDate" -> root.get("saleDate");
      case "conciliationDate" -> root.get("saleReconciliationDate");
      case "expectedPaymentDate" -> expectedPaymentDateSortExpression(root, query, cb, descending);

      case "company" -> join(root, "company").get("fantasyName");
      case "establishment" -> join(root, "establishment").get("pvNumber");
      case "acquirer" -> join(root, "acquirer").get("fantasyName");
      case "flag" -> join(root, "flag").get("name");
      case "adjustmentValue" -> join(root, "adjustment").get("adjustmentValue");

      default -> directRootPathOrNull(root, field);
    };
  }

  private Expression<LocalDate> expectedPaymentDateSortExpression(
    Root<TransactionErpEntity> root, CriteriaQuery<?> query, CriteriaBuilder cb, boolean descending) {
    var subquery = query.subquery(LocalDate.class);
    Root<TransactionErpEntity> correlatedRoot = subquery.correlate(root);
    Join<?, ?> installments = correlatedRoot.join("installments", JoinType.LEFT);
    Expression<LocalDate> expectedPaymentDate = installments.get("expectedPaymentDate");

    subquery.select(descending ? cb.greatest(expectedPaymentDate) : cb.least(expectedPaymentDate));

    return subquery;
  }

  private Path<?> directRootPathOrNull(Root<TransactionErpEntity> root, String field) {
    try {
      return root.get(field);
    } catch (IllegalArgumentException ex) {
      return null;
    }
  }
}
