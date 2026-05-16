package com.cardsync.infrastructure.repository.spec;

import com.cardsync.domain.filter.InstallmentsAcqFilter;
import com.cardsync.domain.filter.query.ListQueryDto;
import com.cardsync.domain.filter.query.SortDto;
import com.cardsync.domain.model.InstallmentAcqEntity;
import com.cardsync.domain.model.enums.ModalityEnum;
import com.cardsync.infrastructure.repository.spec.advancedFilters.InstallmentsAcqAdvancedFields;
import com.cardsync.infrastructure.repository.spec.config.BaseSpecificationSupport;
import com.cardsync.infrastructure.repository.spec.config.DateFilterService;
import com.cardsync.infrastructure.repository.spec.config.SpecificationFactory;
import com.cardsync.infrastructure.repository.spec.config.Specs;
import com.cardsync.infrastructure.repository.spec.tableFilters.InstallmentsAcqTableFields;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class InstallmentsAcqSpecs extends BaseSpecificationSupport<InstallmentAcqEntity> {

  private final SpecificationFactory specificationFactory;
  private final InstallmentsAcqTableFields installmentsAcqTableFields;
  private final InstallmentsAcqAdvancedFields installmentsAcqAdvancedFields;

  public InstallmentsAcqSpecs(
    DateFilterService dateFilterService,
    SpecificationFactory specificationFactory,
    InstallmentsAcqTableFields installmentsAcqTableFields,
    InstallmentsAcqAdvancedFields installmentsAcqAdvancedFields
  ) {
    super(dateFilterService);
    this.specificationFactory = specificationFactory;
    this.installmentsAcqTableFields = installmentsAcqTableFields;
    this.installmentsAcqAdvancedFields = installmentsAcqAdvancedFields;
  }

  public Specification<InstallmentAcqEntity> fromQuery(ListQueryDto<InstallmentsAcqFilter> query) {
    Specification<InstallmentAcqEntity> spec = baseFilters(query)
      .and(fetchListAssociations());

    return spec.and(orderByTableSort(query == null ? null : query.sort()));
  }

  public Specification<InstallmentAcqEntity> fromQueryForTotals(ListQueryDto<InstallmentsAcqFilter> query) {
    return baseFilters(query);
  }

  private Specification<InstallmentAcqEntity> baseFilters(ListQueryDto<InstallmentsAcqFilter> query) {
    Specification<InstallmentAcqEntity> spec = Specs.all();

    if (query != null) {
      spec = spec.and(
        specificationFactory.fromTableFilters(
          query.tableFilters(),
          installmentsAcqTableFields.table()
        )
      );

      spec = spec.and(installmentsAcqAdvancedFields.advanced(query.advanced()));

      if (!isBlank(query.globalFilter())) {
        String gf = query.globalFilter();
        spec = spec.and(anyOf(containsPath(gf, "transaction", "nsu")));
      }
    }

    spec = spec.and(
      notInCodes(
        getModalityEnum(),
        ModalityEnum::getCode,
        "transaction",
        "modality"
      )
    );

    return spec;
  }

  private static List<ModalityEnum> getModalityEnum() {
    return List.of(ModalityEnum.DIGITAL_WALLET, ModalityEnum.OUTROS);
  }

  private Specification<InstallmentAcqEntity> fetchListAssociations() {
    return (root, query, cb) -> {
      if (!isCountQuery(query)) {
        fetchIfNotFetched(root, "adjustment");
        fetchIfNotFetched(root, "creditOrder");
        fetchIfNotFetched(root, "releaseBank");
        fetchIfNotFetched(root, "reconciliationBankFile");

        var transaction = fetchIfNotFetched(root, "transaction");
        fetchIfNotFetched(transaction, "flag");
        fetchIfNotFetched(transaction, "company");
        fetchIfNotFetched(transaction, "acquirer");
        fetchIfNotFetched(transaction, "adjustment");
        fetchIfNotFetched(transaction, "processedFile");
        fetchIfNotFetched(transaction, "establishment");

        var salesSummary = fetchIfNotFetched(transaction, "salesSummary");
        var bankingDomicile = fetchIfNotFetched(salesSummary, "bankingDomicile");
        fetchIfNotFetched(bankingDomicile, "bank");
      }

      return cb.conjunction();
    };
  }

  private Specification<InstallmentAcqEntity> orderByTableSort(List<SortDto> sort) {
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

          Expression<?> expression = sortExpression(root, item.field().trim());

          if (expression == null) {
            continue;
          }

          boolean ascending = item.order() == 1;
          orders.add(ascending ? cb.asc(expression) : cb.desc(expression));
        }
      }

      if (orders.isEmpty()) {
        orders.add(cb.desc(root.get("expectedPaymentDate")));
      }

      orders.add(cb.desc(root.get("id")));
      query.orderBy(orders);

      return cb.conjunction();
    };
  }

  private Expression<?> sortExpression(Root<InstallmentAcqEntity> root, String field) {
    return switch (field) {
      case "id" -> root.get("id");
      case "grossValue" -> root.get("grossValue");
      case "paymentDate" -> root.get("paymentDate");
      case "liquidValue" -> root.get("liquidValue");
      case "installment" -> root.get("installment");
      case "discountValue" -> root.get("discountValue");
      case "expectedPaymentDate" -> root.get("expectedPaymentDate");

      case "paymentStatus" -> root.get("paymentStatus");
      case "cancellationDate" -> root.get("cancellationDate");
      case "installmentStatus" -> root.get("installmentStatus");
      case "reconciliationBankProcessedAt" -> root.get("reconciliationBankProcessedAt");

      case "tid" -> join(root, "transaction").get("tid");
      case "machine" -> join(root, "transaction").get("machine");
      case "capture" -> join(root, "transaction").get("capture");
      case "cvNsu", "nsu" -> join(root, "transaction").get("nsu");
      case "saleDate" -> join(root, "transaction").get("saleDate");
      case "modality" -> join(root, "transaction").get("modality");
      case "cardNumber" -> join(root, "transaction").get("cardNumber");
      case "authorization" -> join(root, "transaction").get("authorization");
      case "transactionStatus", "saleStatus" -> join(root, "transaction").get("transactionStatus");
      case "saleReconciliationDate", "conciliationDate" -> join(root, "transaction").get("saleReconciliationDate");

      case "flag" -> join(join(root, "transaction"), "flag").get("name");
      case "company" -> join(join(root, "transaction"), "company").get("fantasyName");
      case "acquirer" -> join(join(root, "transaction"), "acquirer").get("fantasyName");
      case "processedFile" -> join(join(root, "transaction"), "processedFile").get("file");
      case "establishment" -> join(join(root, "transaction"), "establishment").get("pvNumber");
      case "adjustmentValue" -> join(join(root, "transaction"), "adjustment").get("adjustmentValue");

      default -> nestedPathOrDirectRootPath(root, field);
    };
  }
}
