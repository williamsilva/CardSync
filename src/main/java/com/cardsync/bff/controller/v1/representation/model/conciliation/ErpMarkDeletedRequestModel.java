package com.cardsync.bff.controller.v1.representation.model.conciliation;

/**
 * Corpo para marcar uma venda ERP como excluída por ausência na adquirente.
 * - reason: motivo informado pelo usuário (ex.: "INVALID_DATA"). Texto livre/tag.
 * - observations: observação adicional do usuário (opcional).
 */
public record ErpMarkDeletedRequestModel(
  String reason,
  String observations
) {
}