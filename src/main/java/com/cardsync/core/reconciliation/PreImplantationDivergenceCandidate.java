package com.cardsync.core.reconciliation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Um lançamento elegível pro vínculo automático com divergência pré-implantação (ver PreImplantationDivergenceReconciliationService). */
public record PreImplantationDivergenceCandidate(
  UUID releaseBankId,
  String companyName,
  String acquirerName,
  LocalDate releaseDate,
  BigDecimal releaseValue,
  int matchedOrders,
  BigDecimal sumOrders,
  BigDecimal difference
) {}
