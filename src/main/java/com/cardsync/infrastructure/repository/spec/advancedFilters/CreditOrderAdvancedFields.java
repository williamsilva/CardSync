package com.cardsync.infrastructure.repository.spec.advancedFilters;

import com.cardsync.domain.filter.CreditOrderFilter;
import com.cardsync.domain.model.CreditOrderEntity;
import com.cardsync.domain.model.enums.ModalityEnum;
import com.cardsync.domain.model.enums.StatusPaymentBankEnum;
import com.cardsync.domain.model.enums.StatusReconciliationEnum;
import com.cardsync.infrastructure.repository.spec.config.BaseSpecificationSupport;
import com.cardsync.infrastructure.repository.spec.config.DateFilterService;
import com.cardsync.infrastructure.repository.spec.config.Specs;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

@Component
public class CreditOrderAdvancedFields extends BaseSpecificationSupport<CreditOrderEntity> {

  public CreditOrderAdvancedFields(DateFilterService dateFilterService) {
    super(dateFilterService);
  }

  public Specification<CreditOrderEntity> advanced(CreditOrderFilter filter) {
    Specification<CreditOrderEntity> spec = Specs.all();
    if (filter == null) {
      return spec;
    }

    spec = spec.and(contains(filter.rvNumber(), "rvNumber"));
    spec = spec.and(inCodes("originalPvNumber", filter.establishments(), x -> x));
    spec = spec.and(inCodes("statusPaymentBank", filter.statusPaymentBank(), StatusPaymentBankEnum::getCode));
    spec = spec.and(inCodes("salesSummaryStatus", filter.salesSummaryStatus(), StatusReconciliationEnum::getCode));

    spec = spec.and(inPath(filter.modality(), ModalityEnum::getCode, "salesSummary", "modality"));

    return spec;
  }
}