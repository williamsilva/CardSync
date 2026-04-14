package com.cardsync.domain.filter.spec;

import com.cardsync.domain.model.FlagEntity;
import com.cardsync.domain.model.enums.StatusEnum;
import com.cardsync.infrastructure.repository.spec.config.DateFilterService;
import com.cardsync.infrastructure.repository.spec.config.FieldSpec;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class FlagAllowedFields {

  private final DateFilterService dateFilterService;

  public FlagAllowedFields(DateFilterService dateFilterService) {
    this.dateFilterService = dateFilterService;
  }

  public Map<String, FieldSpec<FlagEntity, ?>> table() {
    return Map.ofEntries(
      Map.entry("erpCode",
        FieldSpec.string("erpCode", (root, query) -> root.get("erpCode"))),

      Map.entry("name",
        FieldSpec.string("name", (root, query) -> root.get("name"))),

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