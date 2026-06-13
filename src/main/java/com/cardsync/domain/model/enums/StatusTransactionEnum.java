package com.cardsync.domain.model.enums;

import com.cardsync.domain.exception.BusinessException;
import com.cardsync.domain.exception.ErrorCode;
import lombok.Getter;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Getter
public enum StatusTransactionEnum {

  NULL(0),
  PENDING(1),
  AUTOMATICALLY_RECONCILED(2),
  MANUALLY_RECONCILED(3),
  NOT_RECONCILED(4),
  CANCELED(5),
  DELETED(6),
  PARTIALLY_RECONCILED(7);

  private final int code;

  StatusTransactionEnum(int code) {
    this.code = code;
  }

  private static final Map<Integer, StatusTransactionEnum> BY_CODE =
    Arrays.stream(values())
      .collect(Collectors.toUnmodifiableMap(StatusTransactionEnum::getCode, Function.identity()));

  private static final Map<String, StatusTransactionEnum> BY_NAME =
    Arrays.stream(values())
      .collect(Collectors.toUnmodifiableMap(Enum::name, Function.identity()));

  public static StatusTransactionEnum fromCode(Integer code) {
    if (code == null) return null;

    StatusTransactionEnum value = BY_CODE.get(code);
    if (value == null) {
      throw BusinessException.badRequest(
        ErrorCode.VALIDATION_ERROR,
        "Invalid StatusTransactionEnum code: " + code
      );
    }

    return value;
  }

  public static StatusTransactionEnum fromName(String name) {
    if (name == null || name.isBlank()) return null;

    StatusTransactionEnum value = BY_NAME.get(name.trim().toUpperCase());
    if (value == null) {
      throw BusinessException.badRequest(
        ErrorCode.VALIDATION_ERROR,
        "Invalid StatusTransactionEnum name: " + name
      );
    }

    return value;
  }

  public static Integer toCode(StatusTransactionEnum status) {
    return status != null ? status.code : null;
  }
}
