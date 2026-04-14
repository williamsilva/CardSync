package com.cardsync.domain.filter;

import com.cardsync.domain.model.enums.StatusEnum;

import java.util.List;
import java.util.UUID;

public record ContractFilter(
  UUID id,

  List<String> company,
  List<String> acquirer,
  List<String> establishment,

  List<StatusEnum> status
) {
}
