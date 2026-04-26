package com.cardsync.domain.filter;

import com.cardsync.domain.model.enums.ContractEnum;
import com.cardsync.domain.model.enums.PeriodEnum;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.util.List;
import java.util.UUID;

public record ContractFilter(
  UUID id,

  List<String> company,
  List<String> acquirer,
  List<String> createdBy,
  List<String> establishment,

  List<ContractEnum> contractEnum,

  PeriodEnum periodEndDate,
  PeriodEnum periodStartDate,

  @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
  List<String> startDate,

  @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
  List<String> endDate
) {
}