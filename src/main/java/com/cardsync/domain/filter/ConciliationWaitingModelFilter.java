package com.cardsync.domain.filter;

import com.cardsync.domain.model.enums.*;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.util.List;

public record ConciliationWaitingModelFilter(

  String tid,
  String cvNsu,
  String authorization,

  BigDecimal grossValueEnd,
  BigDecimal liquidValueEnd,
  BigDecimal grossValueStart,
  BigDecimal liquidValueStart,

  List<CaptureEnum> capture,
  List<ModalityEnum> modality,
  List<StatusTransactionReasonEnum> statusTransactionReason,

  List<String> flags,
  List<String> companies,
  List<String> acquirers,
  List<String> establishments,

  PeriodEnum periodSaleDate,

  @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
  List<String> saleDate
) {
}
