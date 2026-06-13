package com.cardsync.domain.filter;

import com.cardsync.domain.model.enums.AdjustmentReasonEnum;
import com.cardsync.domain.model.enums.PeriodEnum;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.util.List;

public record AdjustmentChargeBackRequestsFilter(

  // Texto livre — busca por NSU ou autorização
  String nsu,
  String authorization,
  String rvAdjustment,

  // Listas de entidades (id como String para compatibilidade com o frontend)
  List<String> flags,
  List<String> companies,
  List<String> acquirers,
  List<String> establishments,

  // Motivo do ajuste (tarifa bancária, chargeback, etc.)
  List<AdjustmentReasonEnum> adjustmentReasons,

  // Status do ajuste (código inteiro armazenado em adjustmentStatus)
  List<Integer> adjustmentStatus,

  // Faixas de valor
  BigDecimal adjustmentValueStart,
  BigDecimal adjustmentValueEnd,
  BigDecimal grossValueStart,
  BigDecimal grossValueEnd,
  BigDecimal liquidValueStart,
  BigDecimal liquidValueEnd,

  // Períodos e datas
  PeriodEnum periodAdjustmentDate,
  PeriodEnum periodCreditDate,

  @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
  List<String> adjustmentDate,

  @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
  List<String> creditDate
) {}