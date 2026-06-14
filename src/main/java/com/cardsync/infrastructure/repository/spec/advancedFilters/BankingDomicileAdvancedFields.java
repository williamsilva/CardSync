package com.cardsync.infrastructure.repository.spec.advancedFilters;

import com.cardsync.domain.filter.BankingDomicileFilter;
import com.cardsync.domain.model.BankingDomicileEntity;
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

    // Buscas por texto (startsWith para aproveitar índices)
    /*
    spec = spec.and(equalsTo("agency", a.agency()));
      spec = spec.and(equalsTo("currentAccount", a.currentAccount()));
      spec = spec.and(contains("holderDocument", a.holderDocument()));
      spec = spec.and(contains("holderName", a.holderName()));
      spec = spec.and(equalsTo("active", a.active()));

      if (a.bankId() != null) {
        spec = spec.and(equalsPath(a.bankId(), "bank", "id"));
      }
      if (a.companyId() != null) {
        spec = spec.and(equalsPath(a.companyId(), "company", "id"));
      }
      if (a.establishmentId() != null) {
        spec = spec.and(equalsPath(a.establishmentId(), "establishment", "id"));
      }
     */

    return spec;
  }
}