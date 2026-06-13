package com.cardsync.domain.model.enums;

import com.cardsync.domain.exception.BusinessException;
import com.cardsync.domain.exception.ErrorCode;
import lombok.Getter;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Getter
public enum StatusPaymentBankEnum {
  NULL(0),
  PENDING(1),
  PAID(2),
  NOT_PAID(3),
  DIVERGENT(4),
  CANCELED(5),
  DELETED(6),
  PARTIALLY_PAID(7);

  private final int code;

  StatusPaymentBankEnum(int code) {
    this.code = code;
  }

  private static final Map<Integer, StatusPaymentBankEnum> BY_CODE =
    Arrays.stream(values())
      .collect(Collectors.toUnmodifiableMap(StatusPaymentBankEnum::getCode, Function.identity()));

  public static StatusPaymentBankEnum fromCode(Integer code) {
    if (code == null) return null;

    StatusPaymentBankEnum value = BY_CODE.get(code);
    if (value == null) {
      throw BusinessException.badRequest(
        ErrorCode.VALIDATION_ERROR,
        "Invalid StatusPaymentBankEnum code: " + code
      );
    }

    return value;
  }

  public static Integer toCode(StatusPaymentBankEnum status) {
    return status != null ? status.getCode() : null;
  }
}
