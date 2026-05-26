package com.cardsync.domain.model.enums;

import com.cardsync.domain.exception.BusinessException;
import com.cardsync.domain.exception.ErrorCode;
import lombok.Getter;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Getter
public enum FeeReconciliationStatusEnum {

  NULL(0),
  PENDING(1),
  RECONCILED(2),
  DIVERGENT_RATE(3),
  MISSING_VALID_CONTRACT(4);

  private final int code;

  FeeReconciliationStatusEnum(int code) {
    this.code = code;
  }

  private static final Map<Integer, FeeReconciliationStatusEnum> BY_CODE =
    Arrays.stream(values())
      .collect(Collectors.toUnmodifiableMap(FeeReconciliationStatusEnum::getCode, Function.identity()));

  private static final Map<String, FeeReconciliationStatusEnum> BY_NAME =
    Arrays.stream(values())
      .collect(Collectors.toUnmodifiableMap(Enum::name, Function.identity()));

  public static FeeReconciliationStatusEnum fromCode(Integer code) {
    if (code == null) {
      return null;
    }

    FeeReconciliationStatusEnum value = BY_CODE.get(code);
    if (value == null) {
      throw BusinessException.badRequest(
        ErrorCode.VALIDATION_ERROR,
        "Invalid FeeReconciliationStatusEnum code: " + code
      );
    }

    return value;
  }

  public static FeeReconciliationStatusEnum fromName(String name) {
    if (name == null || name.isBlank()) {
      return null;
    }

    FeeReconciliationStatusEnum value = BY_NAME.get(name.trim().toUpperCase());
    if (value == null) {
      throw BusinessException.badRequest(
        ErrorCode.VALIDATION_ERROR,
        "Invalid FeeReconciliationStatusEnum name: " + name
      );
    }

    return value;
  }

  public static Integer toCode(FeeReconciliationStatusEnum status) {
    return status != null ? status.code : null;
  }
}
