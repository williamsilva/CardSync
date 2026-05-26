package com.cardsync.domain.filter;

import com.cardsync.domain.model.enums.CaptureEnum;
import com.cardsync.domain.model.enums.ModalityEnum;
import com.cardsync.domain.model.enums.PeriodEnum;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.util.List;

public record ContractAuditModelFilter(
  String cvNsu,
  String authorization,

  List<CaptureEnum> capture,

  List<String> flags,
  List<String> companies,
  List<String> acquirers,
  List<String> establishments,
  List<ModalityEnum> modality,

  BigDecimal grossValueEnd,
  BigDecimal grossValueStart,

  BigDecimal appliedFeeValueEnd,
  BigDecimal appliedFeeValueStart,

  BigDecimal differenceValueEnd,
  BigDecimal differenceValueStart,

  BigDecimal liquidValueEnd,
  BigDecimal liquidValueStart,

  PeriodEnum periodSaleDate,

  @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
  List<String> saleDate
) {}
