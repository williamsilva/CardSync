package com.cardsync.infrastructure.repository.spec;

import com.cardsync.core.config.ImplantationDateProvider;
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
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Component
public class TransactionAcqSpecs extends BaseSpecificationSupport<TransactionAcqEntity> {

  private final ImplantationDateProvider implantationDateProvider;
  private final SpecificationFactory specificationFactory;
  private final TransactionAcqTableFields transactionAcqTableFields;
  private final TransactionAcqAdvancedFields transactionAcqAdvancedFields;

  public TransactionAcqSpecs(
    ImplantationDateProvider implantationDateProvider,
    DateFilterService dateFilterService,
    SpecificationFactory specificationFactory,
    TransactionAcqTableFields transactionAcqFields,
    TransactionAcqAdvancedFields transactionAcqAdvancedFields
  ) {
    super(dateFilterService);
    this.implantationDateProvider = implantationDateProvider;
    this.specificationFactory = specificationFactory;
    this.transactionAcqTableFields = transactionAcqFields;
    this.transactionAcqAdvancedFields = transactionAcqAdvancedFields;
  }

  public Specification<TransactionAcqEntity> fromQuery(ListQueryDto<TransactionAcqSalesFilter> query) {
    Specification<TransactionAcqEntity> spec = baseFilters(query)
      .and(fetchListAssociations());

    return spec.and(orderByTableSort(query == null ? null : query.sort()));
  }

  public Specification<TransactionAcqEntity> fromQueryForTotals(ListQueryDto<TransactionAcqSalesFilter> query) {
    return baseFilters(query);
  }

  private Specification<TransactionAcqEntity> baseFilters(ListQueryDto<TransactionAcqSalesFilter> query) {
    Specification<TransactionAcqEntity> spec = Specs.all();

    if (query != null) {
      spec = spec.and(
        specificationFactory.fromTableFilters(
          query.tableFilters(),
          transactionAcqTableFields.table()
        )
      );

      spec = spec.and(transactionAcqAdvancedFields.advanced(query.advanced()));

      if (!isBlank(query.globalFilter())) {
        String gf = query.globalFilter();

        // Usa nsuGlobalFilter (equals ou prefixo) e startsWith para authorization
        // para aproveitar os índices idx_acq_nsu e idx_acq_authorization.
        // containsPath("establishment","pvNumber") foi removido: join + CAST + LIKE bilateral
        // não usa índice e degrada queries sem filtro de estabelecimento.
        spec = spec.and(
          anyOf(
            nsuGlobalFilter(gf, "nsu"),
            startsWith(gf, "authorization")
          )
        );
      }
    }

    spec = spec.and(Specification.not(
      inCodes("modality", getModalityEnum(), ModalityEnum::getCode)
    ));

    spec = spec.and(dateGreaterThanOrEqual("saleDate", implantationDateProvider.get(), false));

    return spec;
  }

  private static List<ModalityEnum> getModalityEnum() {
    return List.of(ModalityEnum.DIGITAL_WALLET);
  }

  private Specification<TransactionAcqEntity> fetchListAssociations() {
    return (root, query, cb) -> {
      if (!isCountQuery(query)) {
        fetchIfNotFetched(root, "flag");
        fetchIfNotFetched(root, "company");
        fetchIfNotFetched(root, "acquirer");
        fetchIfNotFetched(root, "adjustment");
        fetchIfNotFetched(root, "processedFile");
        fetchIfNotFetched(root, "establishment");

        var salesSummary = fetchIfNotFetched(root, "salesSummary");
        var bankingDomicile = fetchIfNotFetched(salesSummary, "bankingDomicile");
        fetchIfNotFetched(bankingDomicile, "bank");

        // distinct apenas na query de dados — evita COUNT(DISTINCT id) com 9 JOINs desnecessários
        query.distinct(true);
      }

      return cb.conjunction();
    };
  }

  private Specification<TransactionAcqEntity> orderByTableSort(List<SortDto> sort) {
    return tableSort(sort, "saleDate", Map.of(
      "conciliationDate",    sortField("saleReconciliationDate"),
      "saleStatus",          sortField("transactionStatus"),
      "captureEnum",         sortField("capture"),
      "captureType",         sortField("capture"),
      "company",             sortJoin("company", "fantasyName"),
      "establishment",       sortJoin("establishment", "pvNumber"),
      "acquirer",            sortJoin("acquirer", "fantasyName"),
      "flag",                sortJoin("flag", "name"),
      "adjustmentValue",     sortJoin("adjustment", "adjustmentValue"),
      "expectedPaymentDate", (root, query, cb, desc) -> installmentDateSort(root, cb, desc)
    ));
  }

  /** Ordena pelo menor/maior expectedPaymentDate das parcelas via JOIN agregado. */
  private Expression<LocalDate> installmentDateSort(
    Root<TransactionAcqEntity> root, CriteriaBuilder cb, boolean descending) {
    Expression<LocalDate> date = join(root, "installments").get("expectedPaymentDate");
    return descending ? cb.greatest(date) : cb.least(date);
  }
}
