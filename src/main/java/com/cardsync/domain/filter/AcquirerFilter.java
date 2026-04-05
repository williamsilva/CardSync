package com.cardsync.domain.filter;

import com.cardsync.domain.model.enums.StatusEnum;
import com.cardsync.domain.model.enums.TypeCompanyEnum;

import java.util.List;
import java.util.UUID;

public record AcquirerFilter(
  UUID id,
  String cnpj,
  String fantasyName,
  String socialReason,
  List<String> createdBy,

  String createdAtTo,
  String createdAtFrom,

  List<StatusEnum> statusEnum
) {
}
