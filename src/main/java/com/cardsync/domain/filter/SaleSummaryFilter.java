package com.cardsync.domain.filter;

import com.cardsync.domain.model.enums.*;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.util.List;
import java.util.UUID;

public record SaleSummaryFilter(
  UUID id,

  String rvNumber,

  List<String> banks,
  List<String> flags,
  List<String> companies,
  List<String> acquirers,
  List<Integer> establishments,

  List<ModalityEnum> modality,
  List<StatusPaymentBankEnum> statusPaymentBank,
  List<StatusTransactionEnum> transactionsStatus,
  List<StatusReconciliationEnum> creditOrderStatus,

  PeriodEnum periodRvDate,

  @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
  List<String> rvDate
) {
}
