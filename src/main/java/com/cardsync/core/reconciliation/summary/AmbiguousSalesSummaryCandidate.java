package com.cardsync.core.reconciliation.summary;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Candidata do lado do Resumo de Vendas num AmbiguousCreditOrderBatch. installmentsLinked/
 * installmentTotal dão ao operador o mesmo contexto que CreditOrderOrphanLinkingService usa
 * automaticamente (parcela já existente para aquele número não pode receber outro vínculo).
 */
public record AmbiguousSalesSummaryCandidate(
  UUID id,
  BigDecimal liquidValue,
  BigDecimal grossValue,
  LocalDate rvDate,
  int installmentsLinked,
  Integer installmentTotal
) {}
