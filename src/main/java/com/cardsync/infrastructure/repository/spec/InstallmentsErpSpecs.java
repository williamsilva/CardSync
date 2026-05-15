package com.cardsync.infrastructure.repository.spec;

import com.cardsync.domain.filter.InstallmentsErpFilter;
import com.cardsync.domain.filter.query.ListQueryDto;
import com.cardsync.domain.filter.query.SortDto;
import com.cardsync.domain.model.InstallmentErpEntity;
import com.cardsync.domain.model.enums.ModalityEnum;
import com.cardsync.infrastructure.repository.spec.advancedFilters.InstallmentsErpAdvancedFields;
import com.cardsync.infrastructure.repository.spec.config.BaseSpecificationSupport;
import com.cardsync.infrastructure.repository.spec.config.DateFilterService;
import com.cardsync.infrastructure.repository.spec.config.SpecificationFactory;
import com.cardsync.infrastructure.repository.spec.config.Specs;
import com.cardsync.infrastructure.repository.spec.tableFilters.InstallmentsErpTableFields;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class InstallmentsErpSpecs extends BaseSpecificationSupport<InstallmentErpEntity> {

  private final SpecificationFactory specificationFactory;
  private final InstallmentsErpTableFields installmentsErpTableFields;
  private final InstallmentsErpAdvancedFields installmentsErpAdvancedFields;

  public InstallmentsErpSpecs(
    DateFilterService dateFilterService,
    SpecificationFactory specificationFactory,
    InstallmentsErpTableFields installmentsErpTableFields,
    InstallmentsErpAdvancedFields installmentsErpAdvancedFields
  ) {
    super(dateFilterService);
    this.specificationFactory = specificationFactory;
    this.installmentsErpTableFields = installmentsErpTableFields;
    this.installmentsErpAdvancedFields = installmentsErpAdvancedFields;
  }

  public Specification<InstallmentErpEntity> fromQuery(ListQueryDto<InstallmentsErpFilter> query) {
    Specification<InstallmentErpEntity> spec = Specs.all();

    spec = spec.and(fetchListAssociations());

    if (query == null) {
      return spec.and(orderByTableSort(null));
    }

    spec = spec.and(
      specificationFactory.fromTableFilters(
        query.tableFilters(),
        installmentsErpTableFields.table()
      )
    );

    spec = spec.and(installmentsErpAdvancedFields.advanced(query.advanced()));

    if (!isBlank(query.globalFilter())) {
      String gf = query.globalFilter();

      spec = spec.and(
        anyOf(
          containsPath(gf, "transaction", "nsu")
        )
      );
    }

    spec = spec.and(
      notInCodes(
        getModalityEnum(),
        ModalityEnum::getCode,
        "transaction",
        "modality"
      )
    );
    return spec.and(orderByTableSort(query.sort()));
  }

  private static List<ModalityEnum> getModalityEnum() {
    return List.of(
      ModalityEnum.DIGITAL_WALLET,
      ModalityEnum.OUTROS
    );
  }

  private Specification<InstallmentErpEntity> fetchListAssociations() {
    return (root, query, cb) -> {
      if (!isCountQuery(query)) {
        fetchIfNotFetched(root, "transaction");
      }

      return cb.conjunction();
    };
  }

  private Specification<InstallmentErpEntity> orderByTableSort(List<SortDto> sort) {
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
        orders.add(cb.asc(join(root, "transaction").get("saleDate")));
      }

      // Desempate estável para paginação.
      orders.add(cb.desc(root.get("id")));
      query.orderBy(orders);

      return cb.conjunction();
    };
  }

  private Expression<?> sortExpression(Root<InstallmentErpEntity> root, String field) {
    return switch (field) {
      // Campos próprios da parcela ERP
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
      case "reconciliationPaymentProcessedAt" -> root.get("reconciliationPaymentProcessedAt");

      // Campos da venda ERP vinculada
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

      // Associações da venda ERP
      case "flag" -> join(join(root, "transaction"), "flag").get("name");
      case "company" -> join(join(root, "transaction"), "company").get("fantasyName");
      case "acquirer" -> join(join(root, "transaction"), "acquirer").get("fantasyName");
      case "processedFile" -> join(join(root, "transaction"), "processedFile").get("file");
      case "establishment" -> join(join(root, "transaction"), "establishment").get("pvNumber");
      case "adjustmentValue" -> join(join(root, "transaction"), "adjustment").get("adjustmentValue");
      case "bankingDomicile" -> join(join(root, "transaction"), "bankingDomicile").get("currentAccount");

      default -> nestedPathOrDirectRootPath(root, field);
    };
  }

}