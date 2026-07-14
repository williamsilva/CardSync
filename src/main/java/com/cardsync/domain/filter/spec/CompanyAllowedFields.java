package com.cardsync.domain.filter.spec;

import com.cardsync.domain.model.CompanyEntity;
import com.cardsync.domain.model.enums.StatusEnum;
import com.cardsync.domain.model.enums.TypeCompanyEnum;
import com.cardsync.infrastructure.repository.spec.config.DateFilterService;
import com.cardsync.infrastructure.repository.spec.config.FieldSpec;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class CompanyAllowedFields {

  private final DateFilterService dateFilterService;

  public CompanyAllowedFields(DateFilterService dateFilterService) {
    this.dateFilterService = dateFilterService;
  }

  public Map<String, FieldSpec<CompanyEntity, ?>> table() {
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
        FieldSpec.uuid("createdBy", (root, query) ->
          root.get("createdBy"))),

      Map.entry("typeCompanyEnum",
        FieldSpec.enumAsIntegerCode(
          "type",
          TypeCompanyEnum.class,
          TypeCompanyEnum::getCode,
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
