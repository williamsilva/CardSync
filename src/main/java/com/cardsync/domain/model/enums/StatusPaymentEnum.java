package com.cardsync.domain.model.enums;

import lombok.Getter;

@Getter
public enum StatusPaymentEnum {
  NULL(0),
  PENDING(1),
  PAID(2),
  NOT_PAID(3),
  DIVERGENT(4);

  private final int code;

  StatusPaymentEnum(int code) {
    this.code = code;
  }
}
