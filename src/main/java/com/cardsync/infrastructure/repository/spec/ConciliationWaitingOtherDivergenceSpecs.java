package com.cardsync.infrastructure.repository.spec;

import com.cardsync.core.config.CardsyncAppProperties;
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
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class ConciliationWaitingOtherDivergenceSpecs extends BaseSpecificationSupport<TransactionErpEntity> {

  private final CardsyncAppProperties appProperties;
  private final SpecificationFactory specificationFactory;
  private final ConciliationWaitingErpTableFields conciliationWaitingTableFields;
  private final ConciliationWaitingErpAdvancedFields conciliationWaitingAdvancedFields;

  public ConciliationWaitingOtherDivergenceSpecs(
    CardsyncAppProperties appProperties,
    DateFilterService dateFilterService,
    SpecificationFactory specificationFactory,
    ConciliationWaitingErpTableFields conciliationWaitingTableFields,
    ConciliationWaitingErpAdvancedFields conciliationWaitingAdvancedFields
  ) {
    super(dateFilterService);
    this.appProperties = appProperties;
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

    }

    spec = spec.and(Specification.not(inCodes("modality", excludedModalities(), ModalityEnum::getCode)));
    spec = spec.and(Specification.not(inCodes("statusTransaction", otherDivergenceStatus(), StatusTransactionEnum::getCode)));
    spec = spec.and(inCodes("statusTransactionReason", otherDivergenceReasons(), StatusTransactionReasonEnum::getCode));
    spec = spec.and(dateGreaterThanOrEqual("saleDate", appProperties.getImplantationDate(), false));

    return spec;
  }

  private static List<ModalityEnum> excludedModalities() {
    return List.of(ModalityEnum.DIGITAL_WALLET);
  }

  private static List<StatusTransactionEnum> otherDivergenceStatus() {
    return List.of(
      StatusTransactionEnum.DELETED,
      StatusTransactionEnum.CANCELED
    );
  }

  private static List<StatusTransactionReasonEnum> otherDivergenceReasons() {
    return List.of(
      StatusTransactionReasonEnum.FLAG_MISMATCH,
      StatusTransactionReasonEnum.VALUE_MISMATCH,
      StatusTransactionReasonEnum.AMBIGUOUS_MATCH,
      StatusTransactionReasonEnum.DIFFERENT_PLANS,
      StatusTransactionReasonEnum.ACQUIRER_MISMATCH,
      StatusTransactionReasonEnum.MANUAL_SWAP_NOT_FOUND
    );
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
        fetchIfNotFetched(root, "bankingDomicile");

        // distinct apenas na query de dados
        query.distinct(true);
      }

      return cb.conjunction();
    };
  }

  private Specification<TransactionErpEntity> orderByTableSort(List<SortDto> sort) {
    return tableSort(sort, "saleDate", Map.of(
      "conciliationDate",  sortField("saleReconciliationDate"),
      "company",           sortJoin("company", "fantasyName"),
      "establishment",     sortJoin("establishment", "pvNumber"),
      "acquirer",          sortJoin("acquirer", "fantasyName"),
      "flag",              sortJoin("flag", "name"),
      "adjustmentValue",   sortJoin("adjustment", "adjustmentValue")
    ));
  }
}