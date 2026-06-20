package com.cardsync.domain.filter;

import com.cardsync.domain.model.enums.ModalityPaymentBankEnum;
import com.cardsync.domain.model.enums.ReleaseCategoryEnum;

import java.util.List;
import java.util.UUID;

public record ReleasesBankFilter(
  UUID id,

  List<String> banks,
  List<String> flags,
  List<String> companies,
  List<String> acquirers,

  List<ReleaseCategoryEnum> releaseCategory,
  List<ModalityPaymentBankEnum> modalityPaymentBank
) {
}