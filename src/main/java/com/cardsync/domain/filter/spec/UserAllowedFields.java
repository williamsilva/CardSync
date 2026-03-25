package com.cardsync.domain.filter.spec;

import com.cardsync.domain.model.UserEntity;

import com.cardsync.domain.model.enums.StatusUserEnum;
import com.cardsync.infrastructure.repository.spec.config.FieldSpec;
import jakarta.persistence.criteria.JoinType;
import java.util.Map;

public final class UserAllowedFields {
  private UserAllowedFields() {}

  public static Map<String, FieldSpec<UserEntity, ?>> table() {
    return Map.ofEntries(
      Map.entry("name", FieldSpec.string("name", r -> r.get("name"))),
      Map.entry("userName", FieldSpec.string("userName", r -> r.get("userName"))),
      Map.entry("document", FieldSpec.string("document", r -> r.get("document"))),

      Map.entry("status",
        FieldSpec.enumCodeByNameOrCode(
          "status",
          StatusUserEnum.class,
          StatusUserEnum::getCode,   // usa getCode() do enum
          r -> r.get("status")   // usa o getter mapeado no entity
        )
      ),


      Map.entry("blockedUntil", FieldSpec.offsetDateTime("blockedUntil", r -> r.get("blockedUntil"))),
      Map.entry("lastLoginAt", FieldSpec.offsetDateTime("lastLoginAt", r -> r.get("lastLoginAt"))),
      Map.entry("passwordChangedAt", FieldSpec.offsetDateTime("passwordChangedAt", r -> r.get("passwordChangedAt"))),
      Map.entry("passwordExpiresAt", FieldSpec.offsetDateTime("passwordExpiresAt", r -> r.get("passwordExpiresAt"))),

      // ✅ nested/join seguro (whitelist): groups.name
      Map.entry("groups.name",
        FieldSpec.joinedString("groups.name", (root, query) ->
          root.joinSet("groups", JoinType.LEFT).get("name")
        )
      )
    );
  }
}
