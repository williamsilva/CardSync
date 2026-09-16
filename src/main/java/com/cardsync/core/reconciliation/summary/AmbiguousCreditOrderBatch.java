package com.cardsync.core.reconciliation.summary;

import java.util.List;
import java.util.UUID;

/**
 * Lote de liquidação (acquirer + pvNumber + rvNumber) com 2+ SalesSummary candidatas e 1+
 * CreditOrder órfã compartilhando a mesma chave — a vinculação automática
 * (CreditOrderOrphanLinkingService) não consegue resolver com segurança porque 2+ candidatas
 * batem com o mesmo valor (ver javadoc daquela classe). Exposto pra revisão/vínculo manual via
 * AmbiguousCreditOrderLinkingService.
 */
public record AmbiguousCreditOrderBatch(
  UUID acquirerId,
  String acquirerName,
  Integer pvNumber,
  Integer rvNumber,
  List<AmbiguousSalesSummaryCandidate> summaries,
  List<AmbiguousCreditOrderCandidate> orders
) {}
