package com.cardsync.infrastructure.repository.spec;

import com.cardsync.core.config.CardsyncAppProperties;
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
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class InstallmentsErpSpecs extends BaseSpecificationSupport<InstallmentErpEntity> {

  private final SpecificationFactory specificationFactory;
  private final InstallmentsErpTableFields installmentsErpTableFields;
  private final InstallmentsErpAdvancedFields installmentsErpAdvancedFields;
  private final CardsyncAppProperties appProperties;

  public InstallmentsErpSpecs(
    DateFilterService dateFilterService,
    SpecificationFactory specificationFactory,
    InstallmentsErpTableFields installmentsErpTableFields,
    InstallmentsErpAdvancedFields installmentsErpAdvancedFields,
    CardsyncAppProperties appProperties
  ) {
    super(dateFilterService);
    this.specificationFactory = specificationFactory;
    this.installmentsErpTableFields = installmentsErpTableFields;
    this.installmentsErpAdvancedFields = installmentsErpAdvancedFields;
    this.appProperties = appProperties;
  }

  public Specification<InstallmentErpEntity> fromQuery(ListQueryDto<InstallmentsErpFilter> query) {
    Specification<InstallmentErpEntity> spec = baseFilters(query)
      .and(fetchListAssociations());

    return spec.and(orderByTableSort(query == null ? null : query.sort()));
  }

  public Specification<InstallmentErpEntity> fromQueryForTotals(ListQueryDto<InstallmentsErpFilter> query) {
    return baseFilters(query);
  }

  private Specification<InstallmentErpEntity> baseFilters(ListQueryDto<InstallmentsErpFilter> query) {
    Specification<InstallmentErpEntity> spec = Specs.all();

    if (query != null) {
      spec = spec.and(
        specificationFactory.fromTableFilters(
          query.tableFilters(),
          installmentsErpTableFields.table()
        )
      );

      spec = spec.and(installmentsErpAdvancedFields.advanced(query.advanced()));

      if (!isBlank(query.globalFilter())) {
        String gf = query.globalFilter();
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

    spec = spec.and(dateJoinGreaterThanOrEqual("transaction", "saleDate", appProperties.getImplantationDate(), false));

    return spec;
  }

  private static List<ModalityEnum> getModalityEnum() {
    return List.of(ModalityEnum.DIGITAL_WALLET, ModalityEnum.OUTROS);
  }

  private Specification<InstallmentErpEntity> fetchListAssociations() {
    return (root, query, cb) -> {
      if (!isCountQuery(query)) {
        fetchIfNotFetched(root, "reconciliationBankFile");
        fetchIfNotFetched(root, "reconciliationPaymentFile");

        var transaction = fetchIfNotFetched(root, "transaction");
        fetchIfNotFetched(transaction, "flag");
        fetchIfNotFetched(transaction, "company");
        fetchIfNotFetched(transaction, "acquirer");
        fetchIfNotFetched(transaction, "adjustment");
        fetchIfNotFetched(transaction, "processedFile");
        fetchIfNotFetched(transaction, "establishment");

        var bankingDomicile = fetchIfNotFetched(transaction, "bankingDomicile");
        fetchIfNotFetched(bankingDomicile, "bank");

        // distinct apenas na query de dados
        query.distinct(true);
      }

      return cb.conjunction();
    };
  }

  private Specification<InstallmentErpEntity> orderByTableSort(List<SortDto> sort) {
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
      Map.entry("bankingDomicile",       sortJoin("transaction", "bankingDomicile", "currentAccount")),
      Map.entry("flag",                  sortJoin("transaction", "flag", "name")),
      Map.entry("company",               sortJoin("transaction", "company", "fantasyName")),
      Map.entry("acquirer",              sortJoin("transaction", "acquirer", "fantasyName")),
      Map.entry("processedFile",         sortJoin("transaction", "processedFile", "file")),
      Map.entry("establishment",         sortJoin("transaction", "establishment", "pvNumber")),
      Map.entry("adjustmentValue",       sortJoin("transaction", "adjustment", "adjustmentValue"))
    ));
  }
}