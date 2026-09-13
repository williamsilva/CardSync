package com.cardsync.bff.controller.v1.representation.model.conciliation;

import java.util.List;
import java.util.UUID;

/**
 * Achado real (auditoria 2026-09-13): nada no sistema validava o total de parcelas declarado
 * (installment) contra a quantidade real de InstallmentErpEntity/InstallmentAcqEntity geradas -
 * ver ErpAcquirerResolutionService, onde essa divergência já ocorreu na prática. As amostras
 * (até 50 ids cada) servem para investigação pontual, não para listar o total.
 */
public record InstallmentConsistencyAuditResult(
  long erpMismatchCount,
  long acquirerMismatchCount,
  List<UUID> erpSampleIds,
  List<UUID> acquirerSampleIds
) {
  public boolean hasMismatches() {
    return erpMismatchCount > 0 || acquirerMismatchCount > 0;
  }
}
