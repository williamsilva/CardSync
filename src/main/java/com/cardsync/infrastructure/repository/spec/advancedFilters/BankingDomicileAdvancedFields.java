package com.cardsync.infrastructure.repository.spec.advancedFilters;

import com.cardsync.domain.filter.BankingDomicileFilter;
import com.cardsync.domain.model.BankingDomicileEntity;
import com.cardsync.domain.model.enums.StatusEnum;
import com.cardsync.infrastructure.repository.spec.config.BaseSpecificationSupport;
import com.cardsync.infrastructure.repository.spec.config.DateFilterService;
import com.cardsync.infrastructure.repository.spec.config.Specs;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

@Component
public class BankingDomicileAdvancedFields extends BaseSpecificationSupport<BankingDomicileEntity> {

  public BankingDomicileAdvancedFields(DateFilterService dateFilterService) {
    super(dateFilterService);
  }

  public Specification<BankingDomicileEntity> advanced(BankingDomicileFilter filter) {
    if (filter == null) {
      return Specs.all();
    }

    Specification<BankingDomicileEntity> spec = Specs.all();

    spec = spec.and(inPath(filter.banks(), BankingDomicileAdvancedFields::parseUuidOrNull, "bank", "id"));
    spec = spec.and(inPath(filter.companies(), BankingDomicileAdvancedFields::parseUuidOrNull, "company", "id"));

    if (filter.active() != null) {
      Integer code = filter.active() ? StatusEnum.ACTIVE.getCode() : StatusEnum.INACTIVE.getCode();
      spec = spec.and(equalsTo("status", code));
    }

    return spec;
  }
}