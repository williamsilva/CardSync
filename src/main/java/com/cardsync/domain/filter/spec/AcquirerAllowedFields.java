package com.cardsync.domain.filter.spec;

import com.cardsync.domain.model.AcquirerEntity;
import com.cardsync.domain.model.enums.StatusEnum;
import com.cardsync.infrastructure.repository.spec.config.DateFilterService;
import com.cardsync.infrastructure.repository.spec.config.FieldSpec;
import jakarta.persistence.criteria.JoinType;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class AcquirerAllowedFields {

  private final DateFilterService dateFilterService;

  public AcquirerAllowedFields(DateFilterService dateFilterService) {
    this.dateFilterService = dateFilterService;
  }

  public Map<String, FieldSpec<AcquirerEntity, ?>> table() {
    return Map.ofEntries(
      Map.entry("cnpj",
        FieldSpec.string("cnpj", (root, query) -> root.get("cnpj"))),

      Map.entry("fantasyName",
        FieldSpec.string("fantasyName", (root, query) -> root.get("fantasyName"))),

      Map.entry("socialReason",
        FieldSpec.string("socialReason", (root, query) -> root.get("socialReason"))),

      Map.entry("createdAt",
        FieldSpec.offsetDateTime("createdAt", (root, query) -> root.get("createdAt"), dateFilterService)),

      Map.entry("createdBy",
        FieldSpec.joinedUuid("createdBy", (root, query) ->
          root.join("createdBy", JoinType.LEFT).get("id"))),

      Map.entry("status",
        FieldSpec.enumAsIntegerCode(
          "status",
          StatusEnum.class,
          StatusEnum::getCode,
          (root, query) -> root.get("status")
        )
      ),

      Map.entry("statusEnum",
        FieldSpec.enumAsIntegerCode(
          "statusEnum",
          StatusEnum.class,
          StatusEnum::getCode,
          (root, query) -> root.get("status")
        )
      )
    );
  }
}