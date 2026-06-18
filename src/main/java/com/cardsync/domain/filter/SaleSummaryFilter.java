package com.cardsync.domain.filter;

import com.cardsync.domain.model.enums.StatusPaymentBankEnum;
import com.cardsync.domain.model.enums.StatusReconciliationEnum;
import com.cardsync.domain.model.enums.StatusTransactionEnum;

import java.util.List;
import java.util.UUID;

public record SaleSummaryFilter(
  UUID id,
  List<String> flags,
  List<String> companies,
  List<String> acquirers,
  List<String> establishments,

  List<StatusPaymentBankEnum> statusPaymentBank,
  List<StatusTransactionEnum> transactionsStatus,
  List<StatusReconciliationEnum> creditOrderStatus
) {
}
