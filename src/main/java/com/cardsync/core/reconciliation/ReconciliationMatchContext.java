package com.cardsync.core.reconciliation;

import java.util.UUID;

record ReconciliationMatchContext(
  UUID companyId,
  UUID acquirerId,
  UUID establishmentId,
  UUID flagId,
  PaymentKind paymentKind
) {

  enum PaymentKind {
    DEBIT,
    CREDIT,
    UNKNOWN
  }

  boolean compatible(ReconciliationMatchContext other) {
    if (other == null) return false;
    if (!sameRequired(companyId, other.companyId)) return false;
    if (!sameOptional(acquirerId, other.acquirerId)) return false;
    if (!sameOptional(establishmentId, other.establishmentId)) return false;
    if (!sameOptional(flagId, other.flagId)) return false;
    return paymentKind == PaymentKind.UNKNOWN
      || other.paymentKind == PaymentKind.UNKNOWN
      || paymentKind == other.paymentKind;
  }

  /**
   * Força do casamento entre dois contextos compatíveis: quanto mais campos
   * coincidem em ambos os lados (não nulos e iguais), maior o valor.
   *
   * Usado apenas como critério de desempate quando há mais de um candidato com
   * o mesmo valor — não altera o resultado de {@link #compatible}. Assim,
   * candidatos com establishment/bandeira/adquirente confirmados são preferidos
   * a candidatos que só batem por empresa, reduzindo cruzamentos indevidos.
   */
  int strength(ReconciliationMatchContext other) {
    if (other == null || !compatible(other)) return Integer.MIN_VALUE;
    int score = 0;
    if (bothMatch(companyId, other.companyId)) score++;
    if (bothMatch(acquirerId, other.acquirerId)) score++;
    if (bothMatch(establishmentId, other.establishmentId)) score++;
    if (bothMatch(flagId, other.flagId)) score++;
    if (paymentKind != PaymentKind.UNKNOWN && paymentKind == other.paymentKind) score++;
    return score;
  }

  private boolean bothMatch(UUID left, UUID right) {
    return left != null && right != null && left.equals(right);
  }

  private boolean sameRequired(UUID left, UUID right) {
    return left != null && right != null && left.equals(right);
  }

  private boolean sameOptional(UUID left, UUID right) {
    return left == null || right == null || left.equals(right);
  }
}