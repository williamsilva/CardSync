package com.cardsync.core.reconciliation;

import java.util.UUID;

record ReconciliationMatchContext(
  UUID companyId,
  UUID acquirerId,
  UUID establishmentId,
  Integer establishmentPv,
  UUID flagId,
  PaymentKind paymentKind
) {

  enum PaymentKind {
    DEBIT,
    CREDIT,
    UNKNOWN
  }

  /**
   * Controla quais dimensões hoje opcionais (coringa quando nulas/desconhecidas em qualquer lado)
   * passam a ser exigidas no matching. Com os três campos em false, {@link #compatible} reproduz
   * exatamente o comportamento legado — é o contrato coberto por
   * ReconciliationMatchContextTest#compatible com MatchStrictness.NONE.
   */
  record MatchStrictness(boolean flagRequired, boolean establishmentRequired, boolean paymentKindRequired) {
    static final MatchStrictness NONE = new MatchStrictness(false, false, false);
  }

  boolean compatible(ReconciliationMatchContext other, MatchStrictness strictness) {
    if (other == null) return false;
    if (!sameRequired(companyId, other.companyId)) return false;
    if (!sameRequired(acquirerId, other.acquirerId)) return false;
    if (!sameOptional(establishmentId, other.establishmentId)) return false;

    boolean establishmentOk = strictness.establishmentRequired()
      ? sameRequired(establishmentPv, other.establishmentPv)
      : sameOptional(establishmentPv, other.establishmentPv);
    if (!establishmentOk) return false;

    boolean flagOk = strictness.flagRequired()
      ? sameRequired(flagId, other.flagId)
      : sameOptional(flagId, other.flagId);
    if (!flagOk) return false;

    if (strictness.paymentKindRequired()) {
      return paymentKind != PaymentKind.UNKNOWN
        && other.paymentKind != PaymentKind.UNKNOWN
        && paymentKind == other.paymentKind;
    }
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
   * candidatos com estabelecimento/bandeira/adquirente confirmados são preferidos
   * a candidatos que só batem por empresa, reduzindo cruzamentos indevidos.
   */
  int strength(ReconciliationMatchContext other, MatchStrictness strictness) {
    if (other == null || !compatible(other, strictness)) return Integer.MIN_VALUE;
    int score = 0;
    if (bothMatch(companyId, other.companyId)) score++;
    if (bothMatch(acquirerId, other.acquirerId)) score++;
    if (bothMatch(establishmentId, other.establishmentId)) score++;
    if (bothMatch(establishmentPv, other.establishmentPv)) score++;
    if (bothMatch(flagId, other.flagId)) score++;
    if (paymentKind != PaymentKind.UNKNOWN && paymentKind == other.paymentKind) score++;
    return score;
  }

  private static <T> boolean bothMatch(T left, T right) {
    return left != null && right != null && left.equals(right);
  }

  private static <T> boolean sameRequired(T left, T right) {
    return left != null && right != null && left.equals(right);
  }

  private static <T> boolean sameOptional(T left, T right) {
    return left == null || right == null || left.equals(right);
  }
}
