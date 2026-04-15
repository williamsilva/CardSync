package com.cardsync.domain.filter.spec;

import com.cardsync.domain.model.ContractEntity;
import com.cardsync.domain.model.enums.StatusEnum;
import com.cardsync.infrastructure.repository.spec.config.DateFilterService;
import com.cardsync.infrastructure.repository.spec.config.FieldSpec;
import jakarta.persistence.criteria.JoinType;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ContractAllowedFields {

  private final DateFilterService dateFilterService;

  public ContractAllowedFields(DateFilterService dateFilterService) {
    this.dateFilterService = dateFilterService;
  }

  public Map<String, FieldSpec<ContractEntity, ?>> table() {
    return Map.ofEntries(
      Map.entry("description",
        FieldSpec.string("description", (root, query) -> root.get("description"))),

      Map.entry("company",
        FieldSpec.string("company", (root, query) -> root.join("company", JoinType.LEFT).get("fantasyName"))),

      Map.entry("acquirer",
        FieldSpec.string("acquirer", (root, query) -> root.join("acquirer", JoinType.LEFT).get("fantasyName"))),

      Map.entry("establishment",
        FieldSpec.string("establishment", (root, query) -> root.join("establishment", JoinType.LEFT).get("pvNumber"))),

      Map.entry("startDate",
        FieldSpec.string("startDate", (root, query) -> root.get("startDate"))),

      Map.entry("endDate",
        FieldSpec.string("endDate", (root, query) -> root.get("endDate"))),

      Map.entry("createdAt",
        FieldSpec.offsetDateTime("createdAt", (root, query) -> root.get("createdAt"), dateFilterService)),

      Map.entry("createdBy",
        FieldSpec.joinedUuid("createdBy", (root, query) -> root.join("createdBy", JoinType.LEFT).get("id"))),

      Map.entry("status",
        FieldSpec.enumAsIntegerCode(
          "status",
          StatusEnum.class,
          StatusEnum::getCode,
          (root, query) -> root.get("status")
        ))
    );
  }
}
