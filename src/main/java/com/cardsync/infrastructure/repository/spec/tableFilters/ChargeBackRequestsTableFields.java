package com.cardsync.infrastructure.repository.spec.tableFilters;

import com.cardsync.domain.model.RequestNoticeEntity;
import com.cardsync.infrastructure.repository.spec.config.DateFilterService;
import com.cardsync.infrastructure.repository.spec.config.FieldSpec;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ChargeBackRequestsTableFields {

  private final DateFilterService dateFilterService;

  public ChargeBackRequestsTableFields(DateFilterService dateFilterService) {
    this.dateFilterService = dateFilterService;
  }

  public Map<String, FieldSpec<RequestNoticeEntity, ?>> table() {
    return Map.ofEntries();
  }
}
