package com.cardsync.bff.controller.v1.representation.model.conciliation;

import java.util.List;
import java.util.UUID;

/**
 * Achado real (auditoria 2026-09-13): quando o parser de arquivo não resolve o domicílio
 * bancário (agência/conta não cadastrados), a antecipação - e a CreditOrder sintética gerada a
 * partir dela - nunca concilia com o extrato bancário (Etapa 7). Antes, o único sinal disso era
 * um log.warn no momento da importação; a amostra (até 50 ids) serve para investigação pontual.
 */
public record AnticipationConsistencyAuditResult(
  long missingBankingDomicileCount,
  List<UUID> missingBankingDomicileSampleIds
) {
  public boolean hasMissingBankingDomicile() {
    return missingBankingDomicileCount > 0;
  }
}
