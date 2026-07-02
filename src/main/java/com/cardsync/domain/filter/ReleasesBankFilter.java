package com.cardsync.domain.filter;

import com.cardsync.domain.model.enums.ModalityPaymentBankEnum;
import com.cardsync.domain.model.enums.PeriodEnum;
import com.cardsync.domain.model.enums.ReleaseCategoryEnum;
import com.cardsync.domain.model.enums.StatusPaymentBankEnum;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.util.List;
import java.util.UUID;

public record ReleasesBankFilter(
  UUID id,

  List<String> banks,
  List<String> flags,
  List<String> companies,
  List<String> acquirers,

  List<ReleaseCategoryEnum> releaseCategory,
  List<StatusPaymentBankEnum> statusPaymentBank,
  List<ModalityPaymentBankEnum> modalityPaymentBank,

  PeriodEnum periodReleaseDate,

  @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
  List<String> releaseDate
) {
}