package com.cardsync.infrastructure.repository.spec.tableFilters;

import com.cardsync.domain.model.BankingDomicileEntity;
import com.cardsync.infrastructure.repository.spec.config.DateFilterService;
import com.cardsync.infrastructure.repository.spec.config.FieldSpec;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class BankingDomicileTableFields {

  private final DateFilterService dateFilterService;

  public BankingDomicileTableFields(DateFilterService dateFilterService) {
    this.dateFilterService = dateFilterService;
  }

  public Map<String, FieldSpec<BankingDomicileEntity, ?>> table() {
    return Map.ofEntries(
      Map.entry("agency",
        FieldSpec.integer("agency", (root, query) -> root.get("agency"))),

      Map.entry("currentAccount",
        FieldSpec.integer("currentAccount", (root, query) -> root.get("currentAccount"))),

      Map.entry("holderDocument",
        FieldSpec.string("holderDocument", (root, query) -> root.get("holderDocument"))),

      Map.entry("holderName",
        FieldSpec.string("holderName", (root, query) -> root.get("holderName"))),

      Map.entry("active",
        FieldSpec.bool("active", (root, query) -> root.get("active"))),

      Map.entry("bankId",
        FieldSpec.joinedUuid("bankId", (root, query) -> root.get("bank").get("id"))),

      Map.entry("companyId",
        FieldSpec.joinedUuid("companyId", (root, query) -> root.get("company").get("id"))),

      Map.entry("establishmentId",
        FieldSpec.joinedUuid("establishmentId", (root, query) -> root.get("establishment").get("id")))
    );
  }
}