package com.cardsync.infrastructure.repository.spec.advancedFilters;

import com.cardsync.domain.filter.ContractAuditModelFilter;
import com.cardsync.domain.model.ContractAuditEntity;
import com.cardsync.domain.model.enums.CaptureEnum;
import com.cardsync.domain.model.enums.ModalityEnum;
import com.cardsync.infrastructure.repository.spec.config.BaseSpecificationSupport;
import com.cardsync.infrastructure.repository.spec.config.DateFilterService;
import com.cardsync.infrastructure.repository.spec.config.Specs;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

@Component
public class ContractAuditAdvancedFields extends BaseSpecificationSupport<ContractAuditEntity> {

  public ContractAuditAdvancedFields(DateFilterService dateFilterService) {
    super(dateFilterService);
  }

  public Specification<ContractAuditEntity> advanced(ContractAuditModelFilter filter) {
    Specification<ContractAuditEntity> spec = Specs.all();

    if (filter == null) {
      return spec;
    }

    // Filtros diretos da Venda
    // nsu é BIGINT: contains() faz LOWER(coluna), que quebra no Postgres (funcionava no
    // MySQL por cast implícito). numberFilter faz igualdade quando o valor é numérico.
    spec = spec.and(numberFilter(filter.cvNsu(), "nsu"));
    spec = spec.and(contains(filter.authorization(), "authorization"));

    spec = spec.and(inCodes("modality", filter.modality(), ModalityEnum::getCode ));

    spec = spec.and(currencyRangeValue("grossValue", filter.grossValueStart(), filter.grossValueEnd()));
    spec = spec.and(currencyRangeValue("liquidValue", filter.liquidValueStart(), filter.liquidValueEnd()));
    spec = spec.and(currencyRangeValue("rateAcquirer", filter.appliedFeeValueStart(), filter.appliedFeeValueEnd()));
    spec = spec.and(currencyRangeValue("differenceValue", filter.differenceValueStart(), filter.differenceValueEnd()));

    spec = spec.and(inPath(filter.flags(), BaseSpecificationSupport::parseUuidOrNull,  "flag", "id"));
    spec = spec.and(inPath(filter.companies(), BaseSpecificationSupport::parseUuidOrNull, "company", "id"));
    spec = spec.and(inPath(filter.acquirers(), BaseSpecificationSupport::parseUuidOrNull, "acquirer", "id"));
    spec = spec.and(inPath(filter.establishments(), BaseSpecificationSupport::parseUuidOrNull,  "establishment", "id"));

    // Filtros aninhados transactionAcq
    spec = spec.and(inPath(filter.capture(), CaptureEnum::getCode,"transactionAcq", "capture"));
    spec = spec.and(offsetDateTimePeriodJoin(
      "transactionAcq", "saleDate", filter.periodSaleDate(), filter.saleDate(),true));

    return spec;
  }


}