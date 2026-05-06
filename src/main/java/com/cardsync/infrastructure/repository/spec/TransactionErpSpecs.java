package com.cardsync.infrastructure.repository.spec;

import com.cardsync.domain.filter.TransactionErpSalesFilter;
import com.cardsync.domain.filter.query.ListQueryDto;
import com.cardsync.domain.filter.query.SortDto;
import com.cardsync.domain.model.TransactionErpEntity;
import com.cardsync.domain.model.enums.ModalityEnum;
import com.cardsync.infrastructure.repository.spec.advancedFilters.TransactionErpAdvancedFields;
import com.cardsync.infrastructure.repository.spec.config.BaseSpecificationSupport;
import com.cardsync.infrastructure.repository.spec.config.DateFilterService;
import com.cardsync.infrastructure.repository.spec.config.SpecificationFactory;
import com.cardsync.infrastructure.repository.spec.config.Specs;
import com.cardsync.infrastructure.repository.spec.tableFilters.TransactionErpTableFields;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Component
public class TransactionErpSpecs extends BaseSpecificationSupport<TransactionErpEntity> {

  private final SpecificationFactory specificationFactory;
  private final TransactionErpTableFields transactionErpTableFields;
  private final TransactionErpAdvancedFields transactionErpAdvancedFields;

  public TransactionErpSpecs(
    DateFilterService dateFilterService,
    SpecificationFactory specificationFactory,
    TransactionErpTableFields transactionErpFields,
    TransactionErpAdvancedFields transactionErpAdvancedFields
  ) {
    super(dateFilterService);
    this.specificationFactory = specificationFactory;
    this.transactionErpTableFields = transactionErpFields;
    this.transactionErpAdvancedFields = transactionErpAdvancedFields;
  }

  public Specification<TransactionErpEntity> fromQuery(ListQueryDto<TransactionErpSalesFilter> query) {
    Specification<TransactionErpEntity> spec = Specs.all();

    spec = spec.and(fetchListAssociations());

    if (query == null) {
      return spec.and(orderByTableSort(null));
    }

    spec = spec.and(
      specificationFactory.fromTableFilters(
        query.tableFilters(),
        transactionErpTableFields.table()
      )
    );

    spec = spec.and(transactionErpAdvancedFields.advanced(query.advanced()));

    if (!isBlank(query.globalFilter())) {
      String gf = query.globalFilter();

      spec = spec.and(
        anyOf(
          contains(gf, "pvNumber" ),
          contains(gf,"nsu"),
          contains(gf,"authorizationCode"),
          containsPath(gf, "company", "fantasyName"),
          containsPath(gf, "company", "document"),
          containsPath(gf, "establishment", "commercialName"),
          containsPath(gf, "establishment", "pvNumber"),
          containsPath(gf, "acquirer", "name"),
          containsPath(gf, "flag", "name"),
          containsPath(gf, "createdBy", "name")
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

  private Specification<TransactionErpEntity> fetchListAssociations() {
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
        orders.add(cb.asc(root.get("saleDate")));
      }

      // Desempate estável para paginação.
      orders.add(cb.desc(root.get("id")));

      query.orderBy(orders);
      query.distinct(true);

      return cb.conjunction();
    };
  }

  private Expression<?> sortExpression(Root<TransactionErpEntity> root, CriteriaQuery<?> query,
    CriteriaBuilder cb, String field, boolean descending ) {
    return switch (field) {
      case "saleDate" -> root.get("saleDate");
      case "conciliationDate" -> root.get("saleReconciliationDate");
      case "expectedPaymentDate" -> expectedPaymentDateSortExpression(root, query, cb, descending);

      case "company" -> root.join("company", JoinType.LEFT).get("fantasyName");
      case "establishment" -> root.join("establishment", JoinType.LEFT).get("commercialName");
      case "acquirer" -> root.join("acquirer", JoinType.LEFT).get("name");
      case "flag" -> root.join("flag", JoinType.LEFT).get("name");

      case "grossValue" -> root.get("grossValue");
      case "discountValue" -> root.get("discountValue");
      case "liquidValue" -> root.get("liquidValue");
      case "adjustmentValue" -> root.join("adjustment", JoinType.LEFT).get("adjustmentValue");

      case "installment" -> root.get("installment");
      case "nsu", "cvNsu" -> root.get("nsu");
      case "authorization" -> root.get("authorization");
      case "transaction" -> root.get("transaction");
      case "tid" -> root.get("tid");
      case "cardNumber" -> root.get("cardNumber");
      case "machine" -> root.get("machine");
      case "saleStatus" -> root.get("transactionStatus");
      case "captureEnum", "captureType" -> root.get("capture");
      case "modality" -> root.get("modality");

      default -> directRootPathOrNull(root, field);
    };
  }

  private Expression<LocalDate> expectedPaymentDateSortExpression(
    Root<TransactionErpEntity> root,  CriteriaQuery<?> query, CriteriaBuilder cb, boolean descending) {
    var subquery = query.subquery(LocalDate.class);
    Root<TransactionErpEntity> correlatedRoot = subquery.correlate(root);
    Join<?, ?> installments = correlatedRoot.join("installments", JoinType.LEFT);
    Expression<LocalDate> creditDate = installments.get("creditDate");

    // Como expectedPaymentDate vem de uma coleção, ordenar direto pelo join pode quebrar
    // paginação/distinct. Para ASC usamos a menor data da venda; para DESC, a maior.
    subquery.select(descending ? cb.greatest(creditDate) : cb.least(creditDate));

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
