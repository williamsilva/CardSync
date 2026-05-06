package com.cardsync.domain.model.enums;

import lombok.Getter;

@Getter
public enum CaptureEnum {
  NULL(0),
  POS(1),
  PDV(2),
  MANUAL(3),
  ECOMMERCE(4);

  private final int code;

  CaptureEnum(int code) {
    this.code = code;
  }
}
