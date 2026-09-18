package com.cardsync.infrastructure.repository.spec.advancedFilters;

import com.cardsync.domain.filter.AnticipationFilter;
import com.cardsync.domain.model.AnticipationEntity;
import com.cardsync.domain.model.enums.StatusPaymentBankEnum;
import com.cardsync.domain.model.enums.StatusReconciliationEnum;
import com.cardsync.infrastructure.repository.spec.config.BaseSpecificationSupport;
import com.nimbussystems.commons.legacy.filter.spec.DateFilterService;
import com.nimbussystems.commons.legacy.filter.spec.Specs;
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
    spec = spec.and(inPath(filter.transactionsStatus(), StatusReconciliationEnum::getCode,"salesSummary", "transactionsStatus"));

    spec = spec.and(inPath(filter.flags(), BaseSpecificationSupport::parseUuidOrNull,"flag", "id"));
    spec = spec.and(inPath(filter.companies(), BaseSpecificationSupport::parseUuidOrNull,"company", "id"));
    spec = spec.and(inPath(filter.acquirers(), BaseSpecificationSupport::parseUuidOrNull,"acquirer", "id"));
    spec = spec.and(inPath(filter.establishments(), BaseSpecificationSupport::parseUuidOrNull,"establishment", "id"));

    spec = spec.and(currencyRangeValue("grossValue", filter.grossValueStart(), filter.grossValueEnd()));
    spec = spec.and(currencyRangeValue("releaseValue", filter.releaseValueStart(), filter.releaseValueEnd()));
    spec = spec.and(currencyRangeValue("discountRateValue", filter.discountRateValueStart(), filter.discountRateValueEnd()));
    spec = spec.and(currencyRangeValue("originalCreditValue", filter.originalCreditValueStart(), filter.originalCreditValueEnd()));
    // advanceDiscountValue não é coluna própria de AnticipationEntity — é o custo da antecipação
    // (originalCreditValue - releaseValue: o que a venda renderia na data normal menos o que foi
    // de fato antecipado), calculado sob demanda aqui e no assembler que monta a resposta da API
    // (achado real 2026-09-11: nunca tinha sido calculado em lugar nenhum, a coluna sempre
    // aparecia vazia em produção). currencyRangeDiff, não currencyRangeValue - esse último exige
    // root.get(field) apontar pra uma coluna real.
    spec = spec.and(currencyRangeDiff(
      "originalCreditValue", "releaseValue", filter.advanceDiscountValueStart(), filter.advanceDiscountValueEnd()
    ));

    return spec;
  }
}
