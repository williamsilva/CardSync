package com.cardsync.core.reconciliation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Um lançamento sem nenhuma ordem de crédito candidata, elegível pra marcação como legado (ver NoCreditOrderLegacyMarkingService). */
public record NoCreditOrderLegacyCandidate(
  UUID releaseBankId,
  String companyName,
  String acquirerName,
  String bankName,
  LocalDate releaseDate,
  BigDecimal releaseValue
) {}
