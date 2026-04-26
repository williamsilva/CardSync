package com.cardsync.domain.filter;

import com.cardsync.domain.model.enums.PeriodEnum;
import com.cardsync.domain.model.enums.StatusEnum;
import com.cardsync.domain.model.enums.TypeCompanyEnum;
import com.cardsync.domain.model.enums.TypeEstablishmentEnum;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.util.List;
import java.util.UUID;

public record EstablishmentFilter(
  UUID id,
  String pvNumber,

  List<String> company,
  List<String> acquirer,
  List<String> createdBy,

  List<StatusEnum> statusEnum,
  List<TypeEstablishmentEnum>typeEnum,

  PeriodEnum periodCreatedAt,

  @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
  List<String> createdAt
) {
}
