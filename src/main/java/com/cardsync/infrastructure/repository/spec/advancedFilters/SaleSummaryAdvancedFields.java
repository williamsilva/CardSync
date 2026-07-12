package com.cardsync.infrastructure.repository.spec.advancedFilters;

import com.cardsync.domain.filter.SaleSummaryFilter;
import com.cardsync.domain.model.SalesSummaryEntity;
import com.cardsync.domain.model.enums.ModalityEnum;
import com.cardsync.domain.model.enums.StatusPaymentBankEnum;
import com.cardsync.domain.model.enums.StatusReconciliationEnum;
import com.cardsync.domain.model.enums.StatusTransactionEnum;
import com.cardsync.infrastructure.repository.spec.config.BaseSpecificationSupport;
import com.cardsync.infrastructure.repository.spec.config.DateFilterService;
import com.cardsync.infrastructure.repository.spec.config.Specs;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

@Component
public class SaleSummaryAdvancedFields extends BaseSpecificationSupport<SalesSummaryEntity> {

  public SaleSummaryAdvancedFields(DateFilterService dateFilterService) {
    super(dateFilterService);
  }

  public Specification<SalesSummaryEntity> advanced(SaleSummaryFilter filter) {
    Specification<SalesSummaryEntity> spec = Specs.all();
    if (filter == null) {
      return spec;
    }

    spec = spec.and(contains(filter.rvNumber(), "rvNumber"));

    spec = spec.and(localDatePeriod("rvDate", filter.periodRvDate(), filter.rvDate(), true));

    spec = spec.and(inCodes("pvNumber", filter.establishments(), x -> x));
    spec = spec.and(inCodes("modality", filter.modality(), ModalityEnum::getCode));
    spec = spec.and(inCodes("statusPaymentBank", filter.statusPaymentBank(), StatusPaymentBankEnum::getCode));
    spec = spec.and(inCodes("transactionsStatus", filter.transactionsStatus(), StatusTransactionEnum::getCode));
    spec = spec.and(inCodes("creditOrderStatus", filter.creditOrderStatus(), StatusReconciliationEnum::getCode));

    spec = spec.and(inPath(filter.flags(), BaseSpecificationSupport::parseUuidOrNull,"flag", "id"));
    spec = spec.and(inPath(filter.companies(), BaseSpecificationSupport::parseUuidOrNull,"company", "id"));
    spec = spec.and(inPath(filter.acquirers(), BaseSpecificationSupport::parseUuidOrNull,"acquirer", "id"));
    spec = spec.and(inPath(filter.banks(), BaseSpecificationSupport::parseUuidOrNull,"bankingDomicile","bank", "id"));

    return spec;
  }
}
