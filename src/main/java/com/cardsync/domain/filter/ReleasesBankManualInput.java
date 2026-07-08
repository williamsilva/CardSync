package com.cardsync.domain.filter;

import com.cardsync.domain.model.enums.ModalityPaymentBankEnum;
import com.cardsync.domain.model.enums.ReleaseCategoryEnum;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ReleasesBankManualInput(
  UUID companyId,
  UUID bankingDomicileId,
  LocalDate releaseDate,
  BigDecimal releaseValue,
  ReleaseCategoryEnum releaseCategory,
  ModalityPaymentBankEnum modalityPaymentBank,
  String description,
  String document,
  Integer historicalCodeBank,
  UUID acquirerId,
  UUID establishmentId,
  UUID flagId
) {
}
