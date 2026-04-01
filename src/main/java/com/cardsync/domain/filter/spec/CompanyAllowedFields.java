package com.cardsync.domain.filter.spec;

import com.cardsync.domain.model.CompanyEntity;
import com.cardsync.domain.model.enums.StatusEnum;
import com.cardsync.domain.model.enums.TypeCompanyEnum;
import com.cardsync.infrastructure.repository.spec.config.FieldSpec;

import java.util.Map;

public class CompanyAllowedFields {

  private CompanyAllowedFields() {}

  public static Map<String, FieldSpec<CompanyEntity, ?>> table() {
    return Map.ofEntries(
      Map.entry("cnpj", FieldSpec.string("name", r -> r.get("name"))),
      Map.entry("createdBy", FieldSpec.string("createdBy", r -> r.get("createdBy"))),
      Map.entry("fantasyName", FieldSpec.string("fantasyName", r -> r.get("fantasyName"))),
      Map.entry("socialReason", FieldSpec.string("socialReason", r -> r.get("socialReason"))),

      Map.entry("type",
        FieldSpec.enumCodeByNameOrCode(
          "type",
          TypeCompanyEnum.class,
          TypeCompanyEnum::getCode,
          r -> r.get("type")
        )
      ),

      Map.entry("status",
        FieldSpec.enumCodeByNameOrCode(
          "status",
          StatusEnum.class,
          StatusEnum::getCode,
          r -> r.get("status")
        )
      )
    );
  }
}
