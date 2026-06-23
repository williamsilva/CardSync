package com.cardsync.domain.filter;

import com.cardsync.domain.model.enums.StatusPaymentBankEnum;
import com.cardsync.domain.model.enums.StatusReconciliationEnum;

import java.util.List;

public record CreditOrderFilter(
  String rvNumber,

  List<Integer> establishments,

  List<StatusPaymentBankEnum> statusPaymentBank,
  List<StatusReconciliationEnum> salesSummaryStatus
) {
}
