package com.cardsync.domain.filter.spec;

import com.cardsync.domain.model.EmailLogEntity;
import com.cardsync.domain.model.enums.EmailLogEventTypeEnum;
import com.cardsync.domain.model.enums.EmailLogStatusEnum;
import com.cardsync.infrastructure.repository.spec.config.DateFilterService;
import com.cardsync.infrastructure.repository.spec.config.FieldSpec;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class EmailLogAllowedFields {

  private final DateFilterService dateFilterService;

  private EmailLogAllowedFields(DateFilterService dateFilterService) {
    this.dateFilterService = dateFilterService;
  }

  public Map<String, FieldSpec<EmailLogEntity, ?>> table() {
    return Map.ofEntries(
      Map.entry("subject", FieldSpec.string("subject", (root, query) -> root.get("subject"))),
      Map.entry("template", FieldSpec.string("template", (root, query) -> root.get("template"))),
      Map.entry("recipient", FieldSpec.string("recipient", (root, query) -> root.get("recipient"))),

      Map.entry("sentAt",
        FieldSpec.offsetDateTime("sentAt", (root, query) -> root.get("sentAt"), dateFilterService)),

      Map.entry("status",
        FieldSpec.enumAsIntegerCode(
          "status",
          EmailLogStatusEnum.class,
          EmailLogStatusEnum::getCode,
          (root, query) -> root.get("status")
        )
      ),

      Map.entry("eventType",
        FieldSpec.enumAsIntegerCode(
          "eventType",
          EmailLogEventTypeEnum.class,
          EmailLogEventTypeEnum::getCode,
          (root, query) -> root.get("eventType")
        )
      )
    );
  }
}
