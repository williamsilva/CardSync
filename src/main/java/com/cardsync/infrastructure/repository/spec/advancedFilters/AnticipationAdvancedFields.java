package com.cardsync.infrastructure.repository.spec.advancedFilters;

import com.cardsync.domain.filter.AnticipationFilter;
import com.cardsync.domain.model.AnticipationEntity;
import com.cardsync.domain.model.enums.StatusPaymentBankEnum;
import com.cardsync.domain.model.enums.StatusTransactionEnum;
import com.cardsync.infrastructure.repository.spec.config.BaseSpecificationSupport;
import com.cardsync.infrastructure.repository.spec.config.DateFilterService;
import com.cardsync.infrastructure.repository.spec.config.Specs;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

@Component
public class AnticipationAdvancedFields extends BaseSpecificationSupport<AnticipationEntity> {

  public AnticipationAdvancedFields(DateFilterService dateFilterService) {
    super(dateFilterService);
  }

  public Specification<AnticipationEntity> advanced(AnticipationFilter filter) {
    Specification<AnticipationEntity> spec = Specs.all();
    if (filter == null) {
      return spec;
    }

    spec = spec.and(inPath(filter.statusPaymentBank(), StatusPaymentBankEnum::getCode,"salesSummary", "statusPaymentBank"));
    spec = spec.and(inPath(filter.transactionsStatus(), StatusTransactionEnum::getCode,"salesSummary", "transactionsStatus"));

    spec = spec.and(inPath(filter.flags(), BaseSpecificationSupport::parseUuidOrNull,"flag", "id"));
    spec = spec.and(inPath(filter.companies(), BaseSpecificationSupport::parseUuidOrNull,"company", "id"));
    spec = spec.and(inPath(filter.acquirers(), BaseSpecificationSupport::parseUuidOrNull,"acquirer", "id"));
    spec = spec.and(inPath(filter.establishments(), BaseSpecificationSupport::parseUuidOrNull,"establishment", "id"));

    return spec;
  }
}
