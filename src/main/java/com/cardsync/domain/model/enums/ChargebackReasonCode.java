package com.cardsync.domain.model.enums;

import lombok.Getter;

import java.util.Arrays;
import java.util.Optional;

@Getter
public enum ChargebackReasonCode {
  CANCEL_CHBK_MAESTRO(9, true),
  CANCELLATIONS_BY_DISPUTES(10, true),
  SALE_DISPUTE_22(22, true),
  SALE_DISPUTE_23(23, true),
  HIPER_SALE_DISPUTE(39, true),
  CHARGEBACK_DEBIT_REVERSAL(52, true),

  CBK_FEE(15, false),
  SALE_CANCELLATION(18, false),
  POS_INACTIVE_CONNECTION_PIN(20, false),
  EXCESS_CHARGEBACK_ADDITIONAL_TRANSFER(24, false),
  POS_PINPAD_CONNECTION_RENTAL(28, false),
  DEBIT_SALE_CANCELLATION(32, false);

  private final int code;
  private final boolean saleChargeback;

  ChargebackReasonCode(int code, boolean saleChargeback) {
    this.code = code;
    this.saleChargeback = saleChargeback;
  }

  public static Optional<ChargebackReasonCode> fromCode(Integer code) {
    if (code == null) return Optional.empty();
    return Arrays.stream(values()).filter(item -> item.code == code).findFirst();
  }

  public static boolean isSaleChargebackReasonCode(Integer code) {
    return fromCode(code).map(ChargebackReasonCode::isSaleChargeback).orElse(false);
  }

  public static boolean isExplicitNonSaleChargebackCode(Integer code) {
    return fromCode(code).map(item -> !item.isSaleChargeback()).orElse(false);
  }
}
