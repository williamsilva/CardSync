package com.cardsync.bff.controller.v1.representation.model.conciliation;

import com.cardsync.domain.model.TransactionAcqEntity;
import com.cardsync.domain.model.TransactionErpEntity;

/**
 * Par usado apenas na listagem de outras divergências.
 *
 * <p>Importante: em outras divergências a venda ERP normalmente ainda não está
 * vinculada em {@code transaction_acq_id}. Por isso o par é montado por busca
 * de candidata, e não por {@code erp.getTransactionAcq()}.</p>
 */
public record ConciliationWaitingOtherDivergencePair(
  TransactionErpEntity erp,
  TransactionAcqEntity acq
) {
}
