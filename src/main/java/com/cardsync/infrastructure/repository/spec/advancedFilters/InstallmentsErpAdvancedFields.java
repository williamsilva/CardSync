package com.cardsync.infrastructure.repository.spec.advancedFilters;

import com.cardsync.domain.filter.InstallmentsErpFilter;
import com.cardsync.domain.model.InstallmentErpEntity;
import com.cardsync.domain.model.enums.CaptureEnum;
import com.cardsync.domain.model.enums.ModalityEnum;
import com.cardsync.domain.model.enums.StatusPaymentBankEnum;
import com.cardsync.domain.model.enums.StatusTransactionEnum;
import com.cardsync.infrastructure.repository.spec.config.BaseSpecificationSupport;
import com.cardsync.infrastructure.repository.spec.config.DateFilterService;
import com.cardsync.infrastructure.repository.spec.config.Specs;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

@Component
public class InstallmentsErpAdvancedFields extends BaseSpecificationSupport<InstallmentErpEntity> {

  public InstallmentsErpAdvancedFields(DateFilterService dateFilterService) {
    super(dateFilterService);
  }

  public Specification<InstallmentErpEntity> advanced(InstallmentsErpFilter filter) {
    Specification<InstallmentErpEntity> spec = Specs.all();

    if (filter == null) {
      return spec;
    }

    // Filtros diretos da parcela
    spec = spec.and(inCodes("statusPaymentBank", filter.statusPaymentBank(), StatusPaymentBankEnum::getCode));
    spec = spec.and(localDatePeriod(
      "expectedPaymentDate", filter.periodExpectedPaymentDate(), filter.expectedPaymentDate(),true));

    spec = spec.and(currencyRangeValue("grossValue", filter.grossValueStart(), filter.grossValueEnd()));
    spec = spec.and(currencyRangeValue("liquidValue", filter.liquidValueStart(), filter.liquidValueEnd()));
    spec = spec.and(currencyRangeValue("discountValue", filter.discountValueStart(), filter.discountValueEnd()));

    // Filtros aninhados na venda ERP
    spec = spec.and(containsPath(filter.tid(),"transaction", "tid"));
    spec = spec.and(containsPath(filter.cvNsu(),"transaction", "nsu"));
    spec = spec.and(containsPath(filter.authorization(),"transaction", "authorization"));
    spec = spec.and(offsetDateTimePeriodJoin("transaction", "saleDate", filter.periodSaleDate(),  filter.saleDate(), true));

    spec = spec.and(inPath(filter.capture(), CaptureEnum::getCode,"transaction", "capture"));
    spec = spec.and(inPath(filter.modality(), ModalityEnum::getCode, "transaction", "modality" ));
    spec = spec.and(inPath(filter.statusTransaction(), StatusTransactionEnum::getCode,"transaction", "statusTransaction"));

    spec = spec.and(inPath(filter.flags(), BaseSpecificationSupport::parseUuidOrNull, "transaction", "flag", "id"));
    spec = spec.and(inPath(filter.companies(), BaseSpecificationSupport::parseUuidOrNull, "transaction", "company", "id"));
    spec = spec.and(inPath(filter.acquirers(), BaseSpecificationSupport::parseUuidOrNull, "transaction", "acquirer", "id"));
    spec = spec.and(inPath(filter.establishments(), BaseSpecificationSupport::parseUuidOrNull, "transaction", "establishment", "id"));

    spec = spec.and(currencyRangeValuePath(
      filter.adjustmentValueStart(), filter.adjustmentValueEnd(),"transaction", "adjustment", "adjustmentValue"));

    return spec;
  }
}