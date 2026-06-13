package com.cardsync.infrastructure.repository.spec.tableFilters;

import com.cardsync.domain.model.SalesSummaryEntity;
import com.cardsync.infrastructure.repository.spec.config.DateFilterService;
import com.cardsync.infrastructure.repository.spec.config.FieldSpec;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class SaleSummaryTableFields {

  private final DateFilterService dateFilterService;

  public SaleSummaryTableFields(DateFilterService dateFilterService) {
    this.dateFilterService = dateFilterService;
  }

  public Map<String, FieldSpec<SalesSummaryEntity, ?>> table() {
    return Map.ofEntries(

    );
  }
}
