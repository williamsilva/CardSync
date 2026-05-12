package com.cardsync.infrastructure.repository.spec;

import com.cardsync.domain.filter.TransactionAcqSalesFilter;
import com.cardsync.domain.filter.query.ListQueryDto;
import com.cardsync.domain.filter.query.SortDto;
import com.cardsync.domain.model.TransactionAcqEntity;
import com.cardsync.domain.model.enums.ModalityEnum;
import com.cardsync.infrastructure.repository.spec.advancedFilters.TransactionAcqAdvancedFields;
import com.cardsync.infrastructure.repository.spec.config.BaseSpecificationSupport;
import com.cardsync.infrastructure.repository.spec.config.DateFilterService;
import com.cardsync.infrastructure.repository.spec.config.SpecificationFactory;
import com.cardsync.infrastructure.repository.spec.config.Specs;
import com.cardsync.infrastructure.repository.spec.tableFilters.TransactionAcqTableFields;
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Component
public class TransactionAcqSpecs extends BaseSpecificationSupport<TransactionAcqEntity> {

  private final SpecificationFactory specificationFactory;
  private final TransactionAcqTableFields transactionAcqTableFields;
  private final TransactionAcqAdvancedFields transactionAcqAdvancedFields;

  public TransactionAcqSpecs(
    DateFilterService dateFilterService,
    SpecificationFactory specificationFactory,
    TransactionAcqTableFields transactionAcqFields,
    TransactionAcqAdvancedFields transactionAcqAdvancedFields
  ) {
    super(dateFilterService);
    this.specificationFactory = specificationFactory;
    this.transactionAcqTableFields = transactionAcqFields;
    this.transactionAcqAdvancedFields = transactionAcqAdvancedFields;
  }

  public Specification<TransactionAcqEntity> fromQuery(ListQueryDto<TransactionAcqSalesFilter> query) {
    Specification<TransactionAcqEntity> spec = Specs.all();

    spec = spec.and(fetchListAssociations());

    if (query == null) {
      return spec.and(orderByTableSort(null));
    }

    spec = spec.and(
      specificationFactory.fromTableFilters(
        query.tableFilters(),
        transactionAcqTableFields.table()
      )
    );

    spec = spec.and(transactionAcqAdvancedFields.advanced(query.advanced()));

    if (!isBlank(query.globalFilter())) {
      String gf = query.globalFilter();

      spec = spec.and(
        anyOf(
          contains(gf, "pvNumber" ),
          contains(gf,"nsu"),
          contains(gf,"authorizationCode")
        )
      );
    }

    spec = spec.and(Specification.not(
      inCodes("modality", getModalityEnum(), ModalityEnum::getCode)
    ));

    return spec.and(orderByTableSort(query.sort()));
  }

  private static List<ModalityEnum> getModalityEnum() {
    return List.of(
      ModalityEnum.DIGITAL_WALLET
    );
  }

  private Specification<TransactionAcqEntity> fetchListAssociations() {
    return (root, query, cb) -> {
      if (!isCountQuery(query)) {
        fetchIfNotFetched(root, "flag");
        fetchIfNotFetched(root, "company");
        fetchIfNotFetched(root, "acquirer");
        fetchIfNotFetched(root, "adjustment");
        fetchIfNotFetched(root, "establishment");

        query.distinct(true);
      }

      return cb.conjunction();
    };
  }

  private Specification<TransactionAcqEntity> orderByTableSort(List<SortDto> sort) {
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
        orders.add(cb.asc(root.get("saleDate")));
      }

      // Desempate estável para paginação.
      orders.add(cb.desc(root.get("id")));

      query.orderBy(orders);
      query.distinct(true);

      return cb.conjunction();
    };
  }

  private Expression<?> sortExpression(Root<TransactionAcqEntity> root, CriteriaQuery<?> query,
    CriteriaBuilder cb, String field, boolean descending ) {
    return switch (field) {
      case "conciliationDate" -> root.get("saleReconciliationDate");
      case "expectedPaymentDate" -> expectedPaymentDateSortExpression(root, query, cb, descending);

      case "company" -> root.join("company", JoinType.LEFT).get("id");
      case "establishment" -> root.join("establishment", JoinType.LEFT).get("commercialName");
      case "acquirer" -> root.join("acquirer", JoinType.LEFT).get("name");
      case "flag" -> root.join("flag", JoinType.LEFT).get("name");
      case "adjustmentValue" -> root.join("adjustment", JoinType.LEFT).get("adjustmentValue");

      case "saleStatus" -> root.get("transactionStatus");
      case "captureEnum", "captureType" -> root.get("capture");

      default -> directRootPathOrNull(root, field);
    };
  }

  private Expression<LocalDate> expectedPaymentDateSortExpression(
    Root<TransactionAcqEntity> root,  CriteriaQuery<?> query, CriteriaBuilder cb, boolean descending) {
    var subquery = query.subquery(LocalDate.class);
    Root<TransactionAcqEntity> correlatedRoot = subquery.correlate(root);
    Join<?, ?> installments = correlatedRoot.join("installments", JoinType.LEFT);
    Expression<LocalDate> creditDate = installments.get("expectedPaymentDate");

    // Como expectedPaymentDate vem de uma coleção, ordenar direto pelo join pode quebrar
    // paginação/distinct. Para ASC usamos a menor data da venda; para DESC, a maior.
    subquery.select(descending ? cb.greatest(creditDate) : cb.least(creditDate));

    return subquery;
  }

  private Path<?> directRootPathOrNull(Root<TransactionAcqEntity> root, String field) {
    try {
      return root.get(field);
    } catch (IllegalArgumentException ex) {
      return null;
    }
  }
}
