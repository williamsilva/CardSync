package com.cardsync.infrastructure.repository.spec.tableFilters;

import com.cardsync.domain.model.ReleasesBankEntity;
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

    );
  }
}