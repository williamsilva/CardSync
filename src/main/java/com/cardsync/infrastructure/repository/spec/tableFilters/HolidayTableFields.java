package com.cardsync.infrastructure.repository.spec.tableFilters;

import com.cardsync.domain.model.HolidayEntity;
import com.cardsync.domain.model.enums.StatusEnum;
import com.cardsync.infrastructure.repository.spec.config.DateFilterService;
import com.cardsync.infrastructure.repository.spec.config.FieldSpec;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class HolidayTableFields {

  private final DateFilterService dateFilterService;

  public HolidayTableFields(DateFilterService dateFilterService) {
    this.dateFilterService = dateFilterService;
  }

  public Map<String, FieldSpec<HolidayEntity, ?>> table() {
    return Map.ofEntries(
      Map.entry("name",
        FieldSpec.string("name", (root, query) -> root.get("name"))),

      Map.entry("holidayDate",
        FieldSpec.localDate("holidayDate", (root, query) -> root.get("holidayDate"), dateFilterService)),

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