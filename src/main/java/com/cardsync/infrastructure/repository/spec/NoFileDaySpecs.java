package com.cardsync.infrastructure.repository.spec;

import com.cardsync.domain.filter.NoFileDayFilter;
import com.cardsync.domain.filter.query.ListQueryDto;
import com.cardsync.domain.filter.query.SortDto;
import com.cardsync.domain.model.NoFileDayEntity;
import com.cardsync.infrastructure.repository.spec.advancedFilters.NoFileDayAdvancedFields;
import com.cardsync.infrastructure.repository.spec.tableFilters.NoFileDayTableFields;
import com.cardsync.infrastructure.repository.spec.config.BaseSpecificationSupport;
import com.cardsync.infrastructure.repository.spec.config.DateFilterService;
import com.cardsync.infrastructure.repository.spec.config.SpecificationFactory;
import com.cardsync.infrastructure.repository.spec.config.Specs;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class NoFileDaySpecs extends BaseSpecificationSupport<NoFileDayEntity> {

  private final NoFileDayTableFields noFileDayTableFields;
  private final SpecificationFactory specificationFactory;
  private final NoFileDayAdvancedFields noFileDayAdvancedFields;

  public NoFileDaySpecs(
    DateFilterService dateFilterService,
    SpecificationFactory specificationFactory,
    NoFileDayTableFields noFileDayTableFields,
    NoFileDayAdvancedFields noFileDayAdvancedFields
  ) {
    super(dateFilterService);
    this.specificationFactory = specificationFactory;
    this.noFileDayTableFields = noFileDayTableFields;
    this.noFileDayAdvancedFields = noFileDayAdvancedFields;
  }

  /** Spec completa: filtros + fetch joins + ordenação. Usada na query de dados. */
  public Specification<NoFileDayEntity> fromQuery(ListQueryDto<NoFileDayFilter> query) {
    return baseFilters(query)
      .and(fetchListAssociations())
      .and(orderByTableSort(query == null ? null : query.sort()));
  }

  // ─── filtros base ──────────────────────────────────────────────────────────
  private Specification<NoFileDayEntity> baseFilters(ListQueryDto<NoFileDayFilter> query) {
    // Filtro fixo: apenas ajustes, independente dos filtros do usuário.
    Specification<NoFileDayEntity> spec = Specs.all();

    if (query != null) {
      spec = spec.and(
        specificationFactory.fromTableFilters(
          query.tableFilters(),
          noFileDayTableFields.table()
        )
      );
      spec = spec.and(noFileDayAdvancedFields.advanced(query.advanced()));
    }

    return spec;
  }

  // ─── fetch joins (apenas na query de dados, nunca no COUNT) ────────────────
  private Specification<NoFileDayEntity> fetchListAssociations() {
    return (root, query, cb) -> {
      if (!isCountQuery(query)) {
        fetchIfNotFetched(root, "acquirer");

        var bankingDomicile = fetchIfNotFetched(root, "bankingDomicile");
        fetchIfNotFetched(bankingDomicile, "bank");
        fetchIfNotFetched(bankingDomicile, "company");

        query.distinct(true);
      }
      return cb.conjunction();
    };
  }

  // ─── ordenação declarativa ─────────────────────────────────────────────────
  private Specification<NoFileDayEntity> orderByTableSort(List<SortDto> sort) {
    return tableSort(sort, "noFileDate", Map.of(
      "bankingDomicile", sortJoin("bankingDomicile", "agency"),
      "acquirer",     sortJoin("acquirer", "fantasyName")
    ));
  }
}