package com.cardsync.core.reconciliation.summary;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Candidata do lado da CreditOrder órfã num AmbiguousCreditOrderBatch. */
public record AmbiguousCreditOrderCandidate(
  UUID id,
  BigDecimal releaseValue,
  LocalDate releaseDate,
  Integer installmentNumber,
  Integer installmentTotal
) {}
