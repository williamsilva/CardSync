package com.cardsync.infrastructure.repository.spec.advancedFilters;

import com.cardsync.domain.filter.ConciliationWaitingModelFilter;
import com.cardsync.domain.model.TransactionErpEntity;
import com.cardsync.domain.model.enums.CaptureEnum;
import com.cardsync.domain.model.enums.ModalityEnum;
import com.cardsync.domain.model.enums.StatusTransactionReasonEnum;
import com.cardsync.infrastructure.repository.spec.config.BaseSpecificationSupport;
import com.cardsync.infrastructure.repository.spec.config.DateFilterService;
import com.cardsync.infrastructure.repository.spec.config.Specs;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

@Component
public class ConciliationWaitingErpAdvancedFields extends BaseSpecificationSupport<TransactionErpEntity> {

  public ConciliationWaitingErpAdvancedFields(DateFilterService dateFilterService) {
    super(dateFilterService);
  }

  public Specification<TransactionErpEntity> advanced(ConciliationWaitingModelFilter filter) {
    Specification<TransactionErpEntity> spec = Specs.all();

    if (filter == null) {
      return spec;
    }

    // Filtros diretos da Venda
    spec = spec.and(contains(filter.tid(), "tid"));
    spec = spec.and(contains(filter.cvNsu(), "nsu"));
    spec = spec.and(contains(filter.authorization(), "authorization"));

    spec = spec.and(offsetDateTimePeriod("saleDate", filter.periodSaleDate(), filter.saleDate(),true));

    spec = spec.and(currencyRangeValue("grossValue", filter.grossValueStart(), filter.grossValueEnd()));
    spec = spec.and(currencyRangeValue("liquidValue", filter.liquidValueStart(), filter.liquidValueEnd()));

    spec = spec.and(inCodes("capture", filter.capture(), CaptureEnum::getCode));
    spec = spec.and(inCodes("modality", filter.modality(), ModalityEnum::getCode ));
    spec = spec.and(inCodes("statusTransactionReason", filter.statusTransactionReason(), StatusTransactionReasonEnum::getCode ));

    spec = spec.and(inPath(filter.flags(), BaseSpecificationSupport::parseUuidOrNull,  "flag", "id"));
    spec = spec.and(inPath(filter.companies(), BaseSpecificationSupport::parseUuidOrNull, "company", "id"));
    spec = spec.and(inPath(filter.acquirers(), BaseSpecificationSupport::parseUuidOrNull, "acquirer", "id"));
    spec = spec.and(inPath(filter.establishments(), BaseSpecificationSupport::parseUuidOrNull,  "establishment", "id"));

    return spec;
  }
}
