package com.cardsync.domain.filter.spec;

import com.cardsync.domain.model.UserEntity;

import com.cardsync.domain.model.enums.StatusUserEnum;
import com.cardsync.infrastructure.repository.spec.config.DateFilterService;
import com.cardsync.infrastructure.repository.spec.config.FieldSpec;
import jakarta.persistence.criteria.JoinType;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class UserAllowedFields {

  private final DateFilterService dateFilterService;

  private UserAllowedFields(DateFilterService dateFilterService) {
    this.dateFilterService = dateFilterService;
  }

  public Map<String, FieldSpec<UserEntity, ?>> table() {
    return Map.ofEntries(
      Map.entry("name",
        FieldSpec.string("name", (root, query) -> root.get("name"))),
      Map.entry("userName",
        FieldSpec.string("userName", (root, query) -> root.get("userName"))),
      Map.entry("document",
        FieldSpec.string("document", (root, query) -> root.get("document"))),
      Map.entry("lastLoginAt",
        FieldSpec.offsetDateTime("lastLoginAt", (root, query) -> root.get("lastLoginAt"), dateFilterService)),

      Map.entry("blockedUntil",
        FieldSpec.offsetDateTime("blockedUntil", (root, query) -> root.get("blockedUntil"), dateFilterService)),
      Map.entry("passwordChangedAt",
        FieldSpec.offsetDateTime("passwordChangedAt", (root, query) -> root.get("passwordChangedAt"), dateFilterService)),
      Map.entry("passwordExpiresAt",
        FieldSpec.offsetDateTime("passwordExpiresAt", (root, query) -> root.get("passwordExpiresAt"), dateFilterService)),

      Map.entry("createdAt",
        FieldSpec.offsetDateTime("createdAt", (root, query) -> root.get("createdAt"), dateFilterService)),

      Map.entry("createdBy",
        FieldSpec.joinedUuid("createdBy", (root, query) ->
          root.join("createdBy", JoinType.LEFT).get("id"))),

      Map.entry("status",
        FieldSpec.enumAsIntegerCode(
          "status",
          StatusUserEnum.class,
          StatusUserEnum::getCode,
          (root, query) -> root.get("status")
        )
      )
    );
  }
}
