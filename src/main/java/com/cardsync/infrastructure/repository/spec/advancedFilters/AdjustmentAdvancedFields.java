package com.cardsync.infrastructure.repository.spec.advancedFilters;

import com.cardsync.domain.filter.AdjustmentFilter;
import com.cardsync.domain.model.AdjustmentEntity;
import com.cardsync.domain.model.enums.AdjustmentReasonEnum;
import com.cardsync.infrastructure.repository.spec.config.BaseSpecificationSupport;
import com.cardsync.infrastructure.repository.spec.config.DateFilterService;
import com.cardsync.infrastructure.repository.spec.config.Specs;
import jakarta.persistence.criteria.Expression;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class AdjustmentAdvancedFields extends BaseSpecificationSupport<AdjustmentEntity> {

  public AdjustmentAdvancedFields(DateFilterService dateFilterService) {
    super(dateFilterService);
  }

  public Specification<AdjustmentEntity> advanced(AdjustmentFilter filter) {
    if (filter == null) {
      return Specs.all();
    }

    Specification<AdjustmentEntity> spec = Specs.all();

    // Buscas por texto (startsWith para aproveitar índices)
    spec = spec.and(nsuGlobalFilter(filter.nsu(), "nsu"));
    spec = spec.and(startsWith(filter.authorization(), "authorization"));

    // RV Ajuste (inteiro)
    if (filter.rvAdjustment() != null && !filter.rvAdjustment().isBlank()) {
      try {
        int rv = Integer.parseInt(filter.rvAdjustment().trim());
        spec = spec.and((root, query, cb) -> cb.equal(root.get("rvNumberAdjustment"), rv));
      } catch (NumberFormatException ignored) {}
    }

    // Motivo do ajuste — lista de enum
    spec = spec.and(inCodes("adjustmentReason", filter.adjustmentReasons(), AdjustmentReasonEnum::getCode));

    // Status do ajuste — lista de inteiros
    spec = spec.and(inCollection("adjustmentStatus", filter.adjustmentStatus()));

    // Entidades relacionadas
    spec = spec.and(inPath(filter.flags(),         AdjustmentAdvancedFields::parseUuidOrNull, "rvFlagAdjustment", "id"));
    spec = spec.and(inPath(filter.companies(),     AdjustmentAdvancedFields::parseUuidOrNull, "company", "id"));
    spec = spec.and(inPath(filter.acquirers(),     AdjustmentAdvancedFields::parseUuidOrNull, "acquirer", "id"));
    spec = spec.and(inPath(filter.establishments(),AdjustmentAdvancedFields::parseUuidOrNull, "establishment", "id"));

    // Faixas de valor
    spec = spec.and(currencyRange("adjustmentValue", filter.adjustmentValueStart(), filter.adjustmentValueEnd()));
    spec = spec.and(currencyRange("grossValue",      filter.grossValueStart(),      filter.grossValueEnd()));
    spec = spec.and(currencyRange("liquidValue",     filter.liquidValueStart(),     filter.liquidValueEnd()));

    // Períodos de data
    spec = spec.and(localDatePeriod("adjustmentDate", filter.periodAdjustmentDate(), filter.adjustmentDate(), true));
    spec = spec.and(localDatePeriod("creditDate",     filter.periodCreditDate(),     filter.creditDate(),     true));

    return spec;
  }

  // ─── helpers de valor monetário ────────────────────────────────────────────
  private Specification<AdjustmentEntity> currencyRange(String field, BigDecimal start, BigDecimal end) {
    if (start == null && end == null) {
      return alwaysTrue();
    }

    if (start != null && end != null && end.compareTo(start) < 0) {
      BigDecimal tmp = start;
      start = end;
      end = tmp;
    }

    BigDecimal finalStart = start;
    BigDecimal finalEnd   = end;

    return (root, query, cb) -> {
      Expression<BigDecimal> path = root.<BigDecimal>get(field);

      if (finalStart != null && finalEnd != null) {
        return cb.between(path, finalStart, finalEnd);
      }
      if (finalStart != null) {
        return cb.greaterThanOrEqualTo(path, finalStart);
      }
      return cb.lessThanOrEqualTo(path, finalEnd);
    };
  }

  // ─── helper para lista de inteiros simples ─────────────────────────────────
  private Specification<AdjustmentEntity> inCollection(String field, java.util.List<Integer> values) {
    if (values == null || values.isEmpty()) {
      return alwaysTrue();
    }
    return (root, query, cb) -> root.get(field).in(values);
  }
}