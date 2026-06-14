package com.cardsync.infrastructure.repository.spec.tableFilters;

import com.cardsync.domain.model.BankEntity;
import com.cardsync.domain.model.enums.StatusEnum;
import com.cardsync.infrastructure.repository.spec.config.DateFilterService;
import com.cardsync.infrastructure.repository.spec.config.FieldSpec;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class BankTableFields {

  private final DateFilterService dateFilterService;

  public BankTableFields(DateFilterService dateFilterService) {
    this.dateFilterService = dateFilterService;
  }

  public Map<String, FieldSpec<BankEntity, ?>> table() {
    return Map.ofEntries(
      Map.entry("code",
        FieldSpec.string("code", (root, query) -> root.get("code"))),

      Map.entry("name",
        FieldSpec.string("name", (root, query) -> root.get("name"))),

      Map.entry("ispb",
        FieldSpec.string("ispb", (root, query) -> root.get("ispb"))),

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