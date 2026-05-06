package com.cardsync.core.reconciliation;

import java.util.UUID;

record ReconciliationMatchContext(
  UUID companyId,
  UUID acquirerId,
  UUID establishmentId,
  UUID bankingDomicileId,
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
    if (!sameOptional(bankingDomicileId, other.bankingDomicileId)) return false;
    if (!sameOptional(flagId, other.flagId)) return false;
    return paymentKind == PaymentKind.UNKNOWN
      || other.paymentKind == PaymentKind.UNKNOWN
      || paymentKind == other.paymentKind;
  }

  private boolean sameRequired(UUID left, UUID right) {
    return left != null && right != null && left.equals(right);
  }

  private boolean sameOptional(UUID left, UUID right) {
    return left == null || right == null || left.equals(right);
  }
}
