package com.cardsync.infrastructure.repository.spec.tableFilters;

import com.cardsync.domain.model.ReleasesBankEntity;
import com.cardsync.domain.model.enums.StatusPaymentBankEnum;
import com.cardsync.infrastructure.repository.spec.config.DateFilterService;
import com.cardsync.infrastructure.repository.spec.config.FieldSpec;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ReleasesBankTableFields {

  private final DateFilterService dateFilterService;

  public ReleasesBankTableFields(DateFilterService dateFilterService) {
    this.dateFilterService = dateFilterService;
  }

  public Map<String, FieldSpec<ReleasesBankEntity, ?>> table() {
    return Map.ofEntries(
      Map.entry("releaseDate",
        FieldSpec.localDate("releaseDate", (root, query) -> root.get("releaseDate"), dateFilterService)),

      Map.entry("bank",
        FieldSpec.joinedUuid("bank", (root, query) -> root.get("bank").get("id"))),

      Map.entry("company",
        FieldSpec.joinedUuid("company", (root, query) -> root.get("company").get("id"))),

      Map.entry("acquirer",
        FieldSpec.joinedUuid("acquirer", (root, query) -> root.get("acquirer").get("id"))),

      Map.entry("flag",
        FieldSpec.joinedUuid("flag", (root, query) -> root.get("flag").get("id"))),

      Map.entry("establishment",
        FieldSpec.joinedUuid("establishment", (root, query) -> root.get("establishment").get("id"))),

      Map.entry("statusPaymentBank",
        FieldSpec.enumAsIntegerCode(
          "statusPaymentBank",
          StatusPaymentBankEnum.class,
          StatusPaymentBankEnum::getCode,
          (root, query) -> root.get("reconciliationStatus")
        ))
    );
  }
}
