package com.cardsync.domain.filter;

import com.cardsync.domain.model.enums.ModalityEnum;
import com.cardsync.domain.model.enums.PeriodEnum;
import com.cardsync.domain.model.enums.StatusPaymentBankEnum;
import com.cardsync.domain.model.enums.StatusReconciliationEnum;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.util.List;

public record CreditOrderFilter(
  String rvNumber,

  List<String> flags,
  List<String> companies,
  List<Integer> establishments,

  List<ModalityEnum> modality,
  List<StatusPaymentBankEnum> statusPaymentBank,
  List<StatusReconciliationEnum> salesSummaryStatus,

  PeriodEnum periodReleaseDate,
  PeriodEnum periodCreditOrderDate,

  @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
  List<String> releaseDate,

  @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
  List<String> creditOrderDate
) {
}
