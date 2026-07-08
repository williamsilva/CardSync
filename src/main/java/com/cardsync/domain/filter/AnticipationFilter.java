package com.cardsync.domain.filter;

import com.cardsync.domain.model.enums.StatusPaymentBankEnum;
import com.cardsync.domain.model.enums.StatusTransactionEnum;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record AnticipationFilter(
  UUID id,
  List<String> flags,
  List<String> companies,
  List<String> acquirers,
  List<String> establishments,

  List<StatusPaymentBankEnum> statusPaymentBank,
  List<StatusTransactionEnum> transactionsStatus,

  BigDecimal grossValueStart,
  BigDecimal grossValueEnd,
  BigDecimal discountRateValueStart,
  BigDecimal discountRateValueEnd,
  BigDecimal releaseValueStart,
  BigDecimal releaseValueEnd,
  BigDecimal originalCreditValueStart,
  BigDecimal originalCreditValueEnd,
  BigDecimal advanceDiscountValueStart,
  BigDecimal advanceDiscountValueEnd

) {
}
