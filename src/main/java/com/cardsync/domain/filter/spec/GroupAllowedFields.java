package com.cardsync.domain.filter.spec;

import com.cardsync.domain.model.GroupEntity;
import com.cardsync.infrastructure.repository.spec.config.FieldSpec;

import java.util.Map;

public final class GroupAllowedFields {
  private GroupAllowedFields() {}

  public static Map<String, FieldSpec<GroupEntity, ?>> table() {
    return Map.ofEntries(
      Map.entry("name", FieldSpec.string("name", r -> r.get("name"))),
      Map.entry("description", FieldSpec.string("description", r -> r.get("description"))),
      Map.entry("createdAt", FieldSpec.offsetDateTime("createdAt", r -> r.get("createdAt")))
    );
  }
}
