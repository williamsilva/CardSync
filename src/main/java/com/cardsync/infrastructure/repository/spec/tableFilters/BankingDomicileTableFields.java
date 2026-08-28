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

      Map.entry("bank",
        FieldSpec.joinedUuid("bank", (root, query) -> root.get("bank").get("id"))),

      Map.entry("company",
        FieldSpec.joinedUuid("company", (root, query) -> root.get("company").get("id")))
    );
  }
}