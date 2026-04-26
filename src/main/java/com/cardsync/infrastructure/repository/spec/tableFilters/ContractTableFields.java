package com.cardsync.infrastructure.repository.spec.tableFilters;

import com.cardsync.domain.model.ContractEntity;
import com.cardsync.domain.model.enums.ContractEnum;
import com.cardsync.infrastructure.repository.spec.config.DateFilterService;
import com.cardsync.infrastructure.repository.spec.config.FieldSpec;
import jakarta.persistence.criteria.JoinType;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ContractTableFields {

  private final DateFilterService dateFilterService;

  public ContractTableFields(DateFilterService dateFilterService) {
    this.dateFilterService = dateFilterService;
  }

  public Map<String, FieldSpec<ContractEntity, ?>> table() {
    return Map.ofEntries(
      Map.entry("description",
        FieldSpec.string("description", (root, query) -> root.get("description"))),

      Map.entry("startDate",
        FieldSpec.string("startDate", (root, query) -> root.get("startDate"))),

      Map.entry("endDate",
        FieldSpec.string("endDate", (root, query) -> root.get("endDate"))),

      Map.entry("createdAt",
        FieldSpec.offsetDateTime(
          "createdAt",
          (root, query) -> root.get("createdAt"),
          dateFilterService
        )),

      Map.entry("createdBy",
        FieldSpec.joinedUuid(
          "createdBy",
          (root, query) -> root.join("createdBy", JoinType.LEFT).get("id")
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

      Map.entry("establishment",
        FieldSpec.joinedUuid(
          "establishment",
          (root, query) -> root.join("establishment", JoinType.LEFT).get("id")
        )),

      /*
       * Campo enviado pela tabela do front.
       */
      Map.entry("statusEnum",
        FieldSpec.enumAsIntegerCode(
          "statusEnum",
          ContractEnum.class,
          ContractEnum::getCode,
          (root, query) -> root.get("status")
        )),

      /*
       * Alias opcional para compatibilidade.
       */
      Map.entry("status",
        FieldSpec.enumAsIntegerCode(
          "status",
          ContractEnum.class,
          ContractEnum::getCode,
          (root, query) -> root.get("status")
        ))
    );
  }
}