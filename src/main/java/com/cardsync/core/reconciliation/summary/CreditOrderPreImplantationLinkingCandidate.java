package com.cardsync.core.reconciliation.summary;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Uma CreditOrder órfã pré-implantação com vínculo exato disponível (mesmo adquirente+PV+RV) —
 * ver CreditOrderPreImplantationLinkingService.
 */
public record CreditOrderPreImplantationLinkingCandidate(
  UUID creditOrderId,
  String companyName,
  String acquirerName,
  Integer rvNumber,
  Integer pvCentralizer,
  LocalDate rvDate,
  LocalDate releaseDate,
  BigDecimal releaseValue,
  UUID matchedSalesSummaryId
) {}
