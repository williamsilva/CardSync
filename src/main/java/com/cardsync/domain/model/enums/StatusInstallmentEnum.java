package com.cardsync.domain.model.enums;

import lombok.Getter;

@Getter
public enum StatusInstallmentEnum {
  NULL(0),
  SCHEDULED(1),
  RECONCILED(2),
  CANCELED(3),
  DIVERGENT(4);

  private final int code;

  StatusInstallmentEnum(int code) {
    this.code = code;
  }
}
