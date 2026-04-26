package com.cardsync.infrastructure.repository.spec.tableFilters;

import com.cardsync.domain.model.EstablishmentEntity;
import com.cardsync.domain.model.enums.StatusEnum;
import com.cardsync.domain.model.enums.TypeEstablishmentEnum;
import com.cardsync.infrastructure.repository.spec.config.DateFilterService;
import com.cardsync.infrastructure.repository.spec.config.FieldSpec;
import jakarta.persistence.criteria.JoinType;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class EstablishmentTableFields {

  private final DateFilterService dateFilterService;

  public EstablishmentTableFields(DateFilterService dateFilterService) {
    this.dateFilterService = dateFilterService;
  }

  public Map<String, FieldSpec<EstablishmentEntity, ?>> table() {
    return Map.ofEntries(
      Map.entry("pvNumber",
        FieldSpec.string("pvNumber", (root, query) -> root.get("pvNumber"))),

      Map.entry("createdAt",
        FieldSpec.offsetDateTime(
          "createdAt",
          (root, query) -> root.get("createdAt"),
          dateFilterService
        )),

      Map.entry("company",
        FieldSpec.joinedUuid(
          "company",
          (root, query) -> root.join("company", JoinType.LEFT).get("id")
        )),

      Map.entry("acquirer",
        FieldSpec.joinedUuid(
          "acquirer",
          (root, query) -> root.join("acquirer", JoinType.LEFT).get("id")
        )),

      Map.entry("createdBy",
        FieldSpec.joinedUuid(
          "createdBy",
          (root, query) -> root.join("createdBy", JoinType.LEFT).get("id")
        )),

      Map.entry("typeEnum",
        FieldSpec.enumAsIntegerCode(
          "type",
          TypeEstablishmentEnum.class,
          TypeEstablishmentEnum::getCode,
          (root, query) -> root.get("type")
        )
      ),

      Map.entry("statusEnum",
        FieldSpec.enumAsIntegerCode(
          "status",
          StatusEnum.class,
          StatusEnum::getCode,
          (root, query) -> root.get("status")
        )
      )
    );
  }
}
