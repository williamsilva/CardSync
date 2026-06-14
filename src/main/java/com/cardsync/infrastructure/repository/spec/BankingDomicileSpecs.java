package com.cardsync.infrastructure.repository.spec;

import com.cardsync.domain.filter.BankingDomicileFilter;
import com.cardsync.domain.filter.query.ListQueryDto;
import com.cardsync.domain.filter.query.SortDto;
import com.cardsync.domain.model.BankingDomicileEntity;
import com.cardsync.infrastructure.repository.spec.advancedFilters.BankingDomicileAdvancedFields;
import com.cardsync.infrastructure.repository.spec.tableFilters.BankingDomicileTableFields;
import com.cardsync.infrastructure.repository.spec.config.BaseSpecificationSupport;
import com.cardsync.infrastructure.repository.spec.config.DateFilterService;
import com.cardsync.infrastructure.repository.spec.config.SpecificationFactory;
import com.cardsync.infrastructure.repository.spec.config.Specs;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class BankingDomicileSpecs extends BaseSpecificationSupport<BankingDomicileEntity> {

  private final SpecificationFactory specificationFactory;
  private final BankingDomicileTableFields bankingDomicileTableFields;
  private final BankingDomicileAdvancedFields bankingDomicileAdvancedFields;

  public BankingDomicileSpecs(
    DateFilterService dateFilterService,
    SpecificationFactory specificationFactory,
    BankingDomicileTableFields bankingDomicileTableFields,
    BankingDomicileAdvancedFields bankingDomicileAdvancedFields
  ) {
    super(dateFilterService);
    this.specificationFactory    = specificationFactory;
    this.bankingDomicileTableFields   = bankingDomicileTableFields;
    this.bankingDomicileAdvancedFields = bankingDomicileAdvancedFields;
  }

  /** Spec completa: filtros + fetch joins + ordenação. Usada na query de dados. */
  public Specification<BankingDomicileEntity> fromQuery(ListQueryDto<BankingDomicileFilter> query) {
    return baseFilters(query)
      .and(fetchListAssociations())
      .and(orderByTableSort(query == null ? null : query.sort()));
  }

  // ─── filtros base ──────────────────────────────────────────────────────────
  private Specification<BankingDomicileEntity> baseFilters(ListQueryDto<BankingDomicileFilter> query) {
    // Filtro fixo: apenas ajustes de tarifa bancária, independente dos filtros do usuário.
    Specification<BankingDomicileEntity> spec = Specs.all();

    if (query != null) {
      spec = spec.and(
        specificationFactory.fromTableFilters(
          query.tableFilters(),
          bankingDomicileTableFields.table()
        )
      );
      spec = spec.and(bankingDomicileAdvancedFields.advanced(query.advanced()));
    }

    return spec;
  }

  // ─── fetch joins (apenas na query de dados, nunca no COUNT) ────────────────
  private Specification<BankingDomicileEntity> fetchListAssociations() {
    return (root, query, cb) -> {
      if (!isCountQuery(query)) {
        fetchIfNotFetched(root, "bank");
        fetchIfNotFetched(root, "company");

        query.distinct(true);
      }
      return cb.conjunction();
    };
  }

  // ─── ordenação declarativa ─────────────────────────────────────────────────
  private Specification<BankingDomicileEntity> orderByTableSort(List<SortDto> sort) {
    return tableSort(sort, "agency", Map.of(
      "bank",        sortJoin("bank", "name"),
      "company",     sortJoin("company", "fantasyName")
    ));
  }
}