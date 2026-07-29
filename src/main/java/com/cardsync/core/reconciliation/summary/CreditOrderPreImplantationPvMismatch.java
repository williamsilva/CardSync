package com.cardsync.core.reconciliation.summary;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Uma CreditOrder órfã pré-implantação cujo adquirente+RV batem com algum SalesSummary, mas o PV
 * diverge (pvCentralizer da ordem x pvNumber do resumo) — nunca vinculada automaticamente por
 * {@link CreditOrderPreImplantationLinkingService#apply}, só reportada pra revisão manual.
 */
public record CreditOrderPreImplantationPvMismatch(
  UUID creditOrderId,
  String companyName,
  String acquirerName,
  Integer rvNumber,
  Integer pvCentralizerOrder,
  Integer pvNumberSummary,
  LocalDate releaseDate,
  BigDecimal releaseValue,
  UUID candidateSalesSummaryId
) {}
