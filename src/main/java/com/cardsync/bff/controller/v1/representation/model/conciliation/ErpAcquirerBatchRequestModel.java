package com.cardsync.bff.controller.v1.representation.model.conciliation;

import java.util.List;
import java.util.UUID;

/**
 * Corpo para operações em lote sobre vendas ERP em conciliação.
 * - transactionIds: vendas ERP alvo.
 * - reason: motivo informado pelo usuário (ex.: "INVALID_DATA"). Opcional.
 * - observations: observação adicional do usuário. Opcional.

 * reason/observations são usados pelas operações de marcação manual (mark-deleted).
 * Operações que não os utilizam simplesmente os ignoram.
 */
public record ErpAcquirerBatchRequestModel(
  List<UUID> transactionIds,
  String reason,
  String observations
) {
}