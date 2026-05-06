package com.cardsync.domain.model.enums;

import lombok.Getter;

@Getter
public enum StatusTransactionEnum {
  NULL(0),
  PENDING(1),
  AUTOMATICALLY_RECONCILED(2),
  MANUALLY_RECONCILED(3),
  NOT_RECONCILED(4),
  CANCELED(5),
  DELETED(6);

  private final int code;

  StatusTransactionEnum(int code) {
    this.code = code;
  }
}
