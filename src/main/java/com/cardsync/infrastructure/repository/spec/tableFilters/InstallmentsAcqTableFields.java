package com.cardsync.infrastructure.repository.spec.tableFilters;

import com.cardsync.domain.model.InstallmentAcqEntity;
import com.cardsync.infrastructure.repository.spec.config.DateFilterService;
import com.cardsync.infrastructure.repository.spec.config.FieldSpec;
import jakarta.persistence.criteria.JoinType;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class InstallmentsAcqTableFields {

  private final DateFilterService dateFilterService;

  public InstallmentsAcqTableFields(DateFilterService dateFilterService) {
    this.dateFilterService = dateFilterService;
  }

  public Map<String, FieldSpec<InstallmentAcqEntity, ?>> table() {
    return Map.ofEntries(
      Map.entry("grossValue",
        FieldSpec.bigDecimal(
          "grossValue",
          (root, query) -> root.get("grossValue"))),

      Map.entry("discountValue",
        FieldSpec.bigDecimal(
          "discountValue",
          (root, query) -> root.get("discountValue"))),

      Map.entry("liquidValue",
        FieldSpec.bigDecimal(
          "liquidValue",
          (root, query) -> root.get("liquidValue"))),

      Map.entry("adjustmentValue",
        FieldSpec.bigDecimal(
          "adjustmentValue",
          (root, query) -> root.join("adjustment", JoinType.LEFT).get("adjustmentValue"))),

      Map.entry("saleDate",
        FieldSpec.offsetDateTime(
          "saleDate",
          (root, query) -> root.get("saleDate"),
          dateFilterService
        ))
    );
  }
}
