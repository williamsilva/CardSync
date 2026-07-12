package com.cardsync.infrastructure.repository.spec;

import com.cardsync.core.config.ImplantationDateProvider;
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
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class InstallmentsAcqSpecs extends BaseSpecificationSupport<InstallmentAcqEntity> {

  private final SpecificationFactory specificationFactory;
  private final InstallmentsAcqTableFields installmentsAcqTableFields;
  private final InstallmentsAcqAdvancedFields installmentsAcqAdvancedFields;
  private final ImplantationDateProvider implantationDateProvider;

  public InstallmentsAcqSpecs(
    DateFilterService dateFilterService,
    SpecificationFactory specificationFactory,
    InstallmentsAcqTableFields installmentsAcqTableFields,
    InstallmentsAcqAdvancedFields installmentsAcqAdvancedFields,
    ImplantationDateProvider implantationDateProvider
  ) {
    super(dateFilterService);
    this.specificationFactory = specificationFactory;
    this.installmentsAcqTableFields = installmentsAcqTableFields;
    this.installmentsAcqAdvancedFields = installmentsAcqAdvancedFields;
    this.implantationDateProvider = implantationDateProvider;
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
        // startsWithPath usa JOIN + LIKE prefixo — aproveita idx_inst_acq_transaction via transaction.nsu
        spec = spec.and(anyOf(startsWithPath(gf, "transaction", "nsu")));
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

    spec = spec.and(dateJoinGreaterThanOrEqual("transaction", "saleDate", implantationDateProvider.get(), false));

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

        // distinct apenas na query de dados
        query.distinct(true);
      }

      return cb.conjunction();
    };
  }

  private Specification<InstallmentAcqEntity> orderByTableSort(List<SortDto> sort) {
    return tableSort(sort, "expectedPaymentDate", Map.ofEntries(
      Map.entry("cvNsu",                 sortJoin("transaction", "nsu")),
      Map.entry("nsu",                   sortJoin("transaction", "nsu")),
      Map.entry("tid",                   sortJoin("transaction", "tid")),
      Map.entry("machine",               sortJoin("transaction", "machine")),
      Map.entry("capture",               sortJoin("transaction", "capture")),
      Map.entry("saleDate",              sortJoin("transaction", "saleDate")),
      Map.entry("modality",              sortJoin("transaction", "modality")),
      Map.entry("cardNumber",            sortJoin("transaction", "cardNumber")),
      Map.entry("authorization",         sortJoin("transaction", "authorization")),
      Map.entry("transactionStatus",     sortJoin("transaction", "transactionStatus")),
      Map.entry("saleStatus",            sortJoin("transaction", "transactionStatus")),
      Map.entry("saleReconciliationDate",sortJoin("transaction", "saleReconciliationDate")),
      Map.entry("conciliationDate",      sortJoin("transaction", "saleReconciliationDate")),
      Map.entry("flag",                  sortJoin("transaction", "flag", "name")),
      Map.entry("company",               sortJoin("transaction", "company", "fantasyName")),
      Map.entry("acquirer",              sortJoin("transaction", "acquirer", "fantasyName")),
      Map.entry("processedFile",         sortJoin("transaction", "processedFile", "file")),
      Map.entry("establishment",         sortJoin("transaction", "establishment", "pvNumber")),
      Map.entry("adjustmentValue",       sortJoin("transaction", "adjustment", "adjustmentValue"))
    ));
  }
}