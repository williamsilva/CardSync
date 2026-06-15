package com.cardsync.infrastructure.repository.spec.tableFilters;

import com.cardsync.domain.model.NoFileDayEntity;
import com.cardsync.domain.model.enums.NoFileDayTypeEnum;
import com.cardsync.domain.model.enums.StatusEnum;
import com.cardsync.infrastructure.repository.spec.config.DateFilterService;
import com.cardsync.infrastructure.repository.spec.config.FieldSpec;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class NoFileDayTableFields {

  private final DateFilterService dateFilterService;

  public NoFileDayTableFields(DateFilterService dateFilterService) {
    this.dateFilterService = dateFilterService;
  }

  public Map<String, FieldSpec<NoFileDayEntity, ?>> table() {
    return Map.ofEntries(
      Map.entry("description",
        FieldSpec.string("description", (root, query) -> root.get("description"))),

      Map.entry("noFileDate",
        FieldSpec.localDate("noFileDate", (root, query) -> root.get("noFileDate"), dateFilterService)),

      Map.entry("fileGroup",
        FieldSpec.string("fileGroup", (root, query) -> root.get("fileGroup"))),

      Map.entry("bankingDomicileId",
        FieldSpec.joinedUuid("bankingDomicileId", (root, query) -> root.get("bankingDomicile").get("id"))),

      Map.entry("acquirerId",
        FieldSpec.joinedUuid("acquirerId", (root, query) -> root.get("acquirer").get("id"))),

      Map.entry("acquirerFileType",
        FieldSpec.string("acquirerFileType", (root, query) -> root.get("acquirerFileType"))),

      Map.entry("dayType",
        FieldSpec.enumAsIntegerCode(
          "dayType",
          NoFileDayTypeEnum.class,
          NoFileDayTypeEnum::getCode,
          (root, query) -> root.get("dayType")
        )),

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