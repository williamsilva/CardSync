package com.cardsync.domain.filter.spec;

import com.cardsync.domain.model.ContractEntity;
import com.cardsync.infrastructure.repository.spec.config.DateFilterService;
import com.cardsync.infrastructure.repository.spec.config.FieldSpec;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ContractAllowedFields {

  private final DateFilterService dateFilterService;

  public ContractAllowedFields(DateFilterService dateFilterService) {
    this.dateFilterService = dateFilterService;
  }

  public Map<String, FieldSpec<ContractEntity, ?>> table() {
    return Map.ofEntries(


    );
  }
}