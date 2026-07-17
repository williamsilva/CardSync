package com.cardsync.infrastructure.repository.spec.advancedFilters;

import com.cardsync.domain.filter.TransactionAcqSalesFilter;
import com.cardsync.domain.model.TransactionAcqEntity;
import com.cardsync.domain.model.enums.CaptureEnum;
import com.cardsync.domain.model.enums.ModalityEnum;
import com.cardsync.domain.model.enums.StatusTransactionEnum;
import com.cardsync.infrastructure.repository.spec.config.BaseSpecificationSupport;
import com.cardsync.infrastructure.repository.spec.config.DateFilterService;
import com.cardsync.infrastructure.repository.spec.config.Specs;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

@Component
public class TransactionAcqAdvancedFields extends BaseSpecificationSupport<TransactionAcqEntity> {

  public TransactionAcqAdvancedFields(DateFilterService dateFilterService) {
    super(dateFilterService);
  }

  public Specification<TransactionAcqEntity> advanced(TransactionAcqSalesFilter filter) {
    Specification<TransactionAcqEntity> spec = Specs.all();
    if (filter == null) {
      return spec;
    }

    // Filtros diretos
    spec = spec.and(contains(filter.tid(), "tid"));
    // nsu/rvNumber são numéricos: contains() faz LOWER(coluna), que quebra no Postgres
    // (funcionava no MySQL por cast implícito). numberFilter faz igualdade quando o
    // valor é numérico, sem LOWER().
    spec = spec.and(numberFilter(filter.cvNsu(), "nsu"));
    spec = spec.and(contains(filter.machine(), "machine"));
    spec = spec.and(numberFilter(filter.rvNumber(), "rvNumber"));
    spec = spec.and(contains(filter.cardNumber(), "cardNumber"));
    spec = spec.and(contains(filter.authorization(), "authorization"));

    spec = spec.and(inCodes("capture", filter.capture(), CaptureEnum::getCode));
    spec = spec.and(inCodes("modality", filter.modality(),  ModalityEnum::getCode));
    spec = spec.and(inCodes("statusTransaction", filter.statusTransaction(), StatusTransactionEnum::getCode));

    spec = spec.and(offsetDateTimePeriod("saleDate", filter.periodSaleDate(), filter.saleDate(),true));

    spec = spec.and(currencyRangeValue("grossValue", filter.grossValueStart(), filter.grossValueEnd()));
    spec = spec.and(currencyRangeValue("liquidValue", filter.liquidValueStart(), filter.liquidValueEnd()));
    spec = spec.and(currencyRangeValue("discountValue", filter.discountValueStart(), filter.discountValueEnd()));

    // Filtros aninhados
    spec = spec.and(inPath(filter.flags(), TransactionAcqAdvancedFields::parseUuidOrNull,"flag", "id"));
    spec = spec.and(inPath(filter.companies(), TransactionAcqAdvancedFields::parseUuidOrNull,"company", "id"));
    spec = spec.and(inPath(filter.acquirers(), TransactionAcqAdvancedFields::parseUuidOrNull, "acquirer", "id"));
    spec = spec.and(inPath(filter.establishments(), TransactionAcqAdvancedFields::parseUuidOrNull, "establishment", "id"));

    spec = spec.and(localDatePeriodJoin("installments", "expectedPaymentDate",
      filter.periodExpectedPaymentDate(), filter.expectedPaymentDate(),true));

    // Filtros aninhados no Ajuste
    spec = spec.and(currencyRangeValuePath(filter.adjustmentValueStart(), filter.adjustmentValueEnd(),"adjustment","adjustmentValue"));

    return spec;
  }
}
