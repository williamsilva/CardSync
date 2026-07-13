package com.cardsync.domain.filter;

import com.cardsync.domain.model.enums.ModalityEnum;
import com.cardsync.domain.model.enums.PeriodEnum;
import com.cardsync.domain.model.enums.StatusPaymentBankEnum;
import com.cardsync.domain.model.enums.StatusReconciliationEnum;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.util.List;

public record CreditOrderFilter(
  String rvNumber,

  List<String> flags,
  List<String> banks,
  List<String> companies,
  List<String> acquirers,
  List<Integer> establishments,

  /** Filtro exato: ordens vinculadas a lançamento(s) bancário(s) específico(s) (releaseBank.id). */
  List<String> releaseBankIds,

  List<ModalityEnum> modality,
  List<StatusPaymentBankEnum> statusPaymentBank,
  List<StatusReconciliationEnum> salesSummaryStatus,

  BigDecimal releaseValueEnd,
  BigDecimal releaseValueStart,

  PeriodEnum periodRvDate,
  PeriodEnum periodReleaseDate,
  PeriodEnum periodCreditOrderDate,

  @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
  List<String> rvDate,

  @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
  List<String> releaseDate,

  @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
  List<String> creditOrderDate
) {
}
