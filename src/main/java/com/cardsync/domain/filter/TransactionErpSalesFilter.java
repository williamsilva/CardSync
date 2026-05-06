package com.cardsync.domain.filter;

import com.cardsync.domain.model.enums.*;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.util.List;

public record TransactionErpSalesFilter(

  String tid,
  String cvNsu,
  String machine,
  String cardNumber,
  String authorization,

  List<String> flags,
  List<String> companies,
  List<String> acquirers,
  List<String> establishments,

  List<CaptureEnum> capture,
  List<ModalityEnum> modality,
  List<StatusTransactionEnum> conciliationStatus,

  PeriodEnum periodSaleDate,
  PeriodEnum periodPaymentDate,
  PeriodEnum periodConciliationDate,
  PeriodEnum periodExpectedPaymentDate,

  @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
  List<String> saleDate,

  @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
  List<String> paymentDate,

  @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
  List<String> conciliationDate,

  @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
  List<String> expectedPaymentDate
) {
}
