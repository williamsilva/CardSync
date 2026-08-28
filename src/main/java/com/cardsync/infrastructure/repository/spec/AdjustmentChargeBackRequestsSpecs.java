package com.cardsync.infrastructure.repository.spec;

import com.cardsync.domain.filter.AdjustmentChargeBackRequestsFilter;
import com.cardsync.domain.filter.query.ColumnFilterDto;
import com.cardsync.domain.filter.query.FilterRuleDto;
import com.cardsync.domain.filter.query.ListQueryDto;
import com.cardsync.domain.filter.query.SortDto;
import com.cardsync.domain.model.RequestNoticeEntity;
import com.cardsync.domain.model.enums.ChargebackRequestReasonEnum;
import com.cardsync.infrastructure.repository.spec.advancedFilters.ChargeBackRequestsAdvancedFields;
import com.cardsync.infrastructure.repository.spec.config.BaseSpecificationSupport;
import com.cardsync.infrastructure.repository.spec.config.DateFilterService;
import com.cardsync.infrastructure.repository.spec.config.SpecificationFactory;
import com.cardsync.infrastructure.repository.spec.config.Specs;
import com.cardsync.infrastructure.repository.spec.tableFilters.ChargeBackRequestsTableFields;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
      spec = spec.and(specificationFactory.fromTableFilters(query.tableFilters(), adjustmentTableFields.table()));
      spec = spec.and(requestReasonColumnFilter(query.tableFilters()));

      spec = spec.and(adjustmentAdvancedFields.advanced(query.advanced()));
    }

    return spec;
  }

  /**
   * "requestReason" fica fora do map de ChargeBackRequestsTableFields de propósito: cada motivo
   * é um bucket que agrupa vários request_code brutos legados (ver ChargebackRequestReasonEnum),
   * e o FieldSpec genérico só converte 1 valor recebido em 1 valor de comparação — não dá pra
   * expandir pra N códigos por esse caminho. Lido direto do tableFilters cru aqui.
   */
  private Specification<RequestNoticeEntity> requestReasonColumnFilter(Map<String, ColumnFilterDto> tableFilters) {
    if (tableFilters == null) {
      return Specs.all();
    }

    ColumnFilterDto columnFilter = tableFilters.get("requestReason");
    if (columnFilter == null || columnFilter.constraints() == null || columnFilter.constraints().isEmpty()) {
      return Specs.all();
    }

    Set<Integer> codes = new LinkedHashSet<>();
    for (FilterRuleDto rule : columnFilter.constraints()) {
      for (String name : toStringList(rule == null ? null : rule.value())) {
        try {
          codes.addAll(ChargebackRequestReasonEnum.valueOf(name.trim().toUpperCase()).getCodes());
        } catch (IllegalArgumentException ignored) {
          // valor desconhecido — ignora, mesmo comportamento do conversor genérico de enum
        }
      }
    }

    if (codes.isEmpty()) {
      return Specs.all();
    }

    return (root, query, cb) -> root.get("requestCode").in(codes);
  }

  private static List<String> toStringList(Object raw) {
    if (raw == null) {
      return List.of();
    }

    if (raw instanceof Collection<?> collection) {
      return collection.stream()
        .filter(java.util.Objects::nonNull)
        .map(String::valueOf)
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .toList();
    }

    String s = String.valueOf(raw).trim();
    return s.isEmpty() ? List.of() : List.of(s);
  }

  // ─── fetch joins (apenas na query de dados, nunca no COUNT) ────────────────

  private Specification<RequestNoticeEntity> fetchListAssociations() {
    return (root, query, cb) -> {
      if (!isCountQuery(query)) {
        fetchIfNotFetched(root, "flag");
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
    return tableSort(sort, "saleDate", Map.of(
      "flag",        sortJoin("flag", "name"),
      "company",     sortJoin("company", "fantasyName"),
      "acquirer",    sortJoin("acquirer", "fantasyName"),
      "establishment", sortJoin("establishment", "pvNumber")
    ));
  }
}