package com.cardsync.infrastructure.repository.spec;

import com.cardsync.domain.filter.AdjustmentChargeBackRequestsFilter;
import com.cardsync.domain.filter.query.ListQueryDto;
import com.cardsync.domain.filter.query.SortDto;
import com.cardsync.domain.model.RequestNoticeEntity;
import com.cardsync.infrastructure.repository.spec.advancedFilters.ChargeBackRequestsAdvancedFields;
import com.cardsync.infrastructure.repository.spec.config.BaseSpecificationSupport;
import com.cardsync.infrastructure.repository.spec.config.DateFilterService;
import com.cardsync.infrastructure.repository.spec.config.SpecificationFactory;
import com.cardsync.infrastructure.repository.spec.config.Specs;
import com.cardsync.infrastructure.repository.spec.tableFilters.ChargeBackRequestsTableFields;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class AdjustmentChargeBackRequestsSpecs extends BaseSpecificationSupport<RequestNoticeEntity> {

  private final SpecificationFactory specificationFactory;
  private final ChargeBackRequestsTableFields adjustmentTableFields;
  private final ChargeBackRequestsAdvancedFields adjustmentAdvancedFields;

  public AdjustmentChargeBackRequestsSpecs(
    DateFilterService dateFilterService,
    SpecificationFactory specificationFactory,
    ChargeBackRequestsTableFields adjustmentTableFields,
    ChargeBackRequestsAdvancedFields adjustmentAdvancedFields
  ) {
    super(dateFilterService);
    this.specificationFactory    = specificationFactory;
    this.adjustmentTableFields   = adjustmentTableFields;
    this.adjustmentAdvancedFields = adjustmentAdvancedFields;
  }

  /** Spec completa: filtros + fetch joins + ordenação. Usada na query de dados. */
  public Specification<RequestNoticeEntity> fromQuery(ListQueryDto<AdjustmentChargeBackRequestsFilter> query) {
    return baseFilters(query)
      .and(fetchListAssociations())
      .and(orderByTableSort(query == null ? null : query.sort()));
  }

  /** Spec somente com filtros, sem fetch joins. Usada na query de COUNT. */
  public Specification<RequestNoticeEntity> fromQueryForTotals(ListQueryDto<AdjustmentChargeBackRequestsFilter> query) {
    return baseFilters(query);
  }

  // ─── filtros base ──────────────────────────────────────────────────────────

  private Specification<RequestNoticeEntity> baseFilters(ListQueryDto<AdjustmentChargeBackRequestsFilter> query) {
    Specification<RequestNoticeEntity> spec = Specs.all();

    if (query != null) {
      spec = spec.and(
        specificationFactory.fromTableFilters(
          query.tableFilters(),
          adjustmentTableFields.table()
        )
      );

      spec = spec.and(adjustmentAdvancedFields.advanced(query.advanced()));
    }

    return spec;
  }

  // ─── fetch joins (apenas na query de dados, nunca no COUNT) ────────────────

  private Specification<RequestNoticeEntity> fetchListAssociations() {
    return (root, query, cb) -> {
      if (!isCountQuery(query)) {
        fetchIfNotFetched(root, "company");
        fetchIfNotFetched(root, "acquirer");
        fetchIfNotFetched(root, "establishment");
        fetchIfNotFetched(root, "salesSummary");

        query.distinct(true);
      }
      return cb.conjunction();
    };
  }

  // ─── ordenação declarativa ─────────────────────────────────────────────────

  private Specification<RequestNoticeEntity> orderByTableSort(List<SortDto> sort) {
    return tableSort(sort, "adjustmentDate", Map.of(
      "flag",        sortJoin("rvFlagAdjustment", "name"),
      "company",     sortJoin("company", "fantasyName"),
      "acquirer",    sortJoin("acquirer", "fantasyName"),
      "establishment", sortJoin("establishment", "pvNumber")
    ));
  }
}