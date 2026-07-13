package com.cardsync.core.reconciliation;

/**
 * Resultado do desfazimento da conciliação entre um lançamento bancário e as
 * ordens de crédito/parcelas vinculadas a ele.
 */
public record UndoBankReconciliationResult(int creditOrdersUnlinked, int installmentsUnlinked) {}
