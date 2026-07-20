package com.cardsync.bff.controller.v1.representation.model.conciliation;

/**
 * Diagnóstico de impacto dos toggles de matching rígido (ver
 * ReconciliationSettingsEntity.flagMatchRequired/establishmentMatchRequired/paymentKindMatchRequired):
 * quantos registros hoje elegíveis para a Etapa 7 (Banco x Ordem de Crédito) ficariam sem
 * poder casar automaticamente se cada regra fosse ligada agora. Consultar antes de ligar
 * qualquer um dos três toggles em produção.
 */
public record ReconciliationStrictModeImpactModel(
  long ordersWithoutFlag,
  long releasesWithoutFlag,
  /** Sanity check — pvCentralizer é sempre preenchido na ingestão; espera-se ~0. */
  long ordersWithoutPvCentralizer,
  long releasesWithoutEstablishment,
  long ordersWithUnknownPaymentKind
) {}
