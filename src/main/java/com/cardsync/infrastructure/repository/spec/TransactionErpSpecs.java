package com.cardsync.infrastructure.repository.spec;

import com.cardsync.core.config.ImplantationDateProvider;
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
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Component
public class TransactionErpSpecs extends BaseSpecificationSupport<TransactionErpEntity> {

  private final SpecificationFactory specificationFactory;
  private final ImplantationDateProvider implantationDateProvider;
  private final TransactionErpTableFields transactionErpTableFields;
  private final TransactionErpAdvancedFields transactionErpAdvancedFields;

  public TransactionErpSpecs(
    DateFilterService dateFilterService,
    SpecificationFactory specificationFactory,
    TransactionErpTableFields transactionErpFields,
    ImplantationDateProvider implantationDateProvider,
    TransactionErpAdvancedFields transactionErpAdvancedFields
  ) {
    super(dateFilterService);
    this.specificationFactory = specificationFactory;
    this.transactionErpTableFields = transactionErpFields;
    this.implantationDateProvider = implantationDateProvider;
    this.transactionErpAdvancedFields = transactionErpAdvancedFields;
  }

  public Specification<TransactionErpEntity> fromQuery(ListQueryDto<TransactionErpSalesFilter> query) {
    Specification<TransactionErpEntity> spec = baseFilters(query)
      .and(fetchListAssociations());

    return spec.and(orderByTableSort(query == null ? null : query.sort()));
  }

  public Specification<TransactionErpEntity> fromQueryForTotals(ListQueryDto<TransactionErpSalesFilter> query) {
    return baseFilters(query);
  }

  private Specification<TransactionErpEntity> baseFilters(ListQueryDto<TransactionErpSalesFilter> query) {
    Specification<TransactionErpEntity> spec = Specs.all();

    if (query != null) {
      spec = spec.and(
        specificationFactory.fromTableFilters(
          query.tableFilters(),
          transactionErpTableFields.table()
        )
      );

      spec = spec.and(transactionErpAdvancedFields.advanced(query.advanced()));

    }

    spec = spec.and(Specification.not(inCodes("modality", getModalityEnum(), ModalityEnum::getCode)));
    spec = spec.and(dateGreaterThanOrEqual("saleDate", implantationDateProvider.get(), false));

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

        var bankingDomicile = fetchIfNotFetched(root, "bankingDomicile");
        fetchIfNotFetched(bankingDomicile, "bank");

        // distinct apenas na query de dados
        query.distinct(true);
      }

      return cb.conjunction();
    };
  }

  private Specification<TransactionErpEntity> orderByTableSort(List<SortDto> sort) {
    return tableSort(sort, "saleDate", Map.of(
      "conciliationDate",    sortField("saleReconciliationDate"),
      "company",             sortJoin("company", "fantasyName"),
      "establishment",       sortJoin("establishment", "pvNumber"),
      "acquirer",            sortJoin("acquirer", "fantasyName"),
      "flag",                sortJoin("flag", "name"),
      "adjustmentValue",     sortJoin("adjustment", "adjustmentValue"),
      "expectedPaymentDate", (root, query, cb, desc) -> installmentDateSort(root, cb, desc)
    ));
  }

  private Expression<LocalDate> installmentDateSort(
    Root<TransactionErpEntity> root, CriteriaBuilder cb, boolean descending) {
    Expression<LocalDate> date = join(root, "installments").get("expectedPaymentDate");
    return descending ? cb.greatest(date) : cb.least(date);
  }
}
