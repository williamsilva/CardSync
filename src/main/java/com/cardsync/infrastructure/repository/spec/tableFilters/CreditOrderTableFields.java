package com.cardsync.infrastructure.repository.spec.tableFilters;

import com.cardsync.domain.model.CreditOrderEntity;
import com.cardsync.infrastructure.repository.spec.config.DateFilterService;
import com.cardsync.infrastructure.repository.spec.config.FieldSpec;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class CreditOrderTableFields {

  private final DateFilterService dateFilterService;

  public CreditOrderTableFields(DateFilterService dateFilterService) {
    this.dateFilterService = dateFilterService;
  }

  public Map<String, FieldSpec<CreditOrderEntity, ?>> table() {
    return Map.ofEntries(

    );
  }
}