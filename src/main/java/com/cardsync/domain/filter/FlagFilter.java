package com.cardsync.domain.filter;

import com.cardsync.domain.model.enums.StatusEnum;

import java.util.List;
import java.util.UUID;

public record FlagFilter(
  UUID id,
  String name,
  Integer erpCode,

  List<StatusEnum> statusEnum
) {
}
