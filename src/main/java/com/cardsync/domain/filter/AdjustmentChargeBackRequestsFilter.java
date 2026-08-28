package com.cardsync.domain.filter;

import com.cardsync.domain.model.enums.ChargebackRequestReasonEnum;
import com.cardsync.domain.model.enums.ChargebackRequestStatusEnum;
import com.cardsync.domain.model.enums.ModalityEnum;
import com.cardsync.domain.model.enums.PeriodEnum;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.util.List;

/**
 * Espelha ChargebackRequestAdvancedFilters (chargeback-request.filters.ts) — este registro
 * substituiu um antigo que era uma cópia colada de AdjustmentFilter (motivo/valor/tarifa de
 * ajuste bancário), sem nenhum campo em comum de verdade com RequestNoticeEntity.
 */
public record AdjustmentChargeBackRequestsFilter(
  List<String> flags,
  List<String> companies,
  List<String> acquirers,
  List<String> establishments,

  PeriodEnum periodSaleDate,
  @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
  List<String> saleDate,

  PeriodEnum periodDeadline,
  @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
  List<String> deadline,

  String cvNsu,
  String authorization,
  String rvNumber,
  String cardNumber,

  List<ModalityEnum> modality,
  List<ChargebackRequestReasonEnum> requestReason,
  List<ChargebackRequestStatusEnum> adjustmentStatus
) {
}
