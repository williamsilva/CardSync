package com.cardsync.domain.filter;

import com.cardsync.domain.model.enums.StatusEnum;
import com.cardsync.domain.model.enums.TypeCompanyEnum;
import com.cardsync.domain.model.enums.TypeEstablishmentEnum;

import java.util.List;
import java.util.UUID;

public record EstablishmentFilter(
  UUID id,
  String pvNumber,

  List<String> company,
  List<String> acquirer,
  List<String> createdBy,

  String createdAtTo,
  String createdAtFrom,

  List<StatusEnum> statusEnum,
  List<TypeEstablishmentEnum>typeEnum
) {
}
