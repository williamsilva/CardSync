package com.cardsync.domain.filter.spec;

import com.cardsync.domain.model.GroupEntity;
import com.cardsync.infrastructure.repository.spec.config.DateFilterService;
import com.cardsync.infrastructure.repository.spec.config.FieldSpec;
import jakarta.persistence.criteria.JoinType;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public final class GroupAllowedFields {

  private final DateFilterService dateFilterService;

  private GroupAllowedFields(DateFilterService dateFilterService) {
    this.dateFilterService = dateFilterService;
  }

  public Map<String, FieldSpec<GroupEntity, ?>> table() {
    return Map.ofEntries(
      Map.entry("name",
        FieldSpec.string("name", (root, query) -> root.get("name"))),
      Map.entry("description",
        FieldSpec.string("description", (root, query) -> root.get("description"))),
      Map.entry("createdAt",
        FieldSpec.offsetDateTime("createdAt", (root, query) -> root.get("createdAt"), dateFilterService)),

      Map.entry("createdBy",
        FieldSpec.joinedUuid("createdBy", (root, query) ->
          root.join("createdBy", JoinType.LEFT).get("id")))
    );
  }
}
