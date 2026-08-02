package com.cardsync.bff.controller.v1.representation.model.bank;

import com.cardsync.domain.model.enums.ModalityPaymentBankEnum;

import java.util.UUID;

public record ReleasesBankManualImportResult(
  UUID id,
  String acquirerName,
  String flagName,
  ModalityPaymentBankEnum modalityPaymentBank,
  Integer establishmentPvNumber
) {
}
