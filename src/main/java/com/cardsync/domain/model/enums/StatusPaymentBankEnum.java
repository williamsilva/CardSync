package com.cardsync.domain.model.enums;

import lombok.Getter;

@Getter
public enum StatusPaymentBankEnum {
  NULL(0),
  PENDING(1),
  PAID(2),
  NOT_PAID(3),
  DIVERGENT(4),
  CANCELED(5),
  DELETED(6);

  private final int code;

  StatusPaymentBankEnum(int code) {
    this.code = code;
  }
}
