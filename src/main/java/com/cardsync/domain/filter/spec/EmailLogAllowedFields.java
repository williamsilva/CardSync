package com.cardsync.domain.filter.spec;

import com.cardsync.domain.model.EmailLogEntity;
import com.cardsync.infrastructure.repository.spec.config.FieldSpec;

import java.util.Map;

public final class EmailLogAllowedFields {
  private EmailLogAllowedFields() {}

  public static Map<String, FieldSpec<EmailLogEntity, ?>> table() {
    return Map.ofEntries(
      Map.entry("name", FieldSpec.string("name", r -> r.get("name"))),
      Map.entry("description", FieldSpec.string("description", r -> r.get("description"))),
      Map.entry("createdAt", FieldSpec.offsetDateTime("createdAt", r -> r.get("createdAt")))
    );
  }
}
