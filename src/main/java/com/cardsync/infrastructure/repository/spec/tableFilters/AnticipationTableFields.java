package com.cardsync.infrastructure.repository.spec.tableFilters;

import com.cardsync.domain.model.AnticipationEntity;
import com.cardsync.infrastructure.repository.spec.config.DateFilterService;
import com.cardsync.infrastructure.repository.spec.config.FieldSpec;
import jakarta.persistence.criteria.JoinType;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class AnticipationTableFields {

  private final DateFilterService dateFilterService;

  public AnticipationTableFields(DateFilterService dateFilterService) {
    this.dateFilterService = dateFilterService;
  }

  public Map<String, FieldSpec<AnticipationEntity, ?>> table() {
    return Map.ofEntries(
      Map.entry("company",
        FieldSpec.joinedUuid(
          "company",
          (root, query) -> root.join("company", JoinType.LEFT).get("id")
        )),

      Map.entry("establishment",
        FieldSpec.integer(
          "establishment",
          (root, query) -> root.join("establishment", JoinType.LEFT).get("pvNumber")
        )),

      Map.entry("flag",
        FieldSpec.joinedUuid(
          "flag",
          (root, query) -> root.join("flag", JoinType.LEFT).get("id")
        ))
    );
  }
}
