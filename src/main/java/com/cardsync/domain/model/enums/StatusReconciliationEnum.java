package com.cardsync.domain.model.enums;

import com.cardsync.domain.exception.BusinessException;
import com.cardsync.domain.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Getter
@AllArgsConstructor
public enum StatusReconciliationEnum {

  NULL(0),
  PENDING(1),
  RECONCILED(2),
  PARTIALLY_RECONCILED(3),
  DIVERGENT(4),
  CANCELED(5);

  private final int code;

  /*
   * Lookup O(1)
   */
  private static final Map<Integer, StatusReconciliationEnum> BY_CODE =
    Arrays.stream(values())
      .collect(Collectors.toUnmodifiableMap(StatusReconciliationEnum::getCode, Function.identity()));

  private static final Map<String, StatusReconciliationEnum> BY_NAME =
    Arrays.stream(values())
      .collect(Collectors.toUnmodifiableMap(Enum::name, Function.identity()));

  /*
   * Converte código do banco -> enum
   */
  public static StatusReconciliationEnum fromCode(Integer code) {

    if (code == null) {
      return null;
    }

    StatusReconciliationEnum value = BY_CODE.get(code);

    if (value == null) {
      throw BusinessException.badRequest(
        ErrorCode.VALIDATION_ERROR,
        "Invalid StatusReconciliationEnum code: " + code
      );
    }

    return value;
  }

  /*
   * Converte string -> enum
   */
  public static StatusReconciliationEnum fromName(String name) {

    if (name == null || name.isBlank()) {
      return null;
    }

    StatusReconciliationEnum value = BY_NAME.get(name.trim().toUpperCase());

    if (value == null) {
      throw BusinessException.badRequest(
        ErrorCode.VALIDATION_ERROR,
        "Invalid StatusReconciliationEnum name: " + name
      );
    }

    return value;
  }

  /*
   * Enum -> código do banco
   */
  public static Integer toCode(StatusReconciliationEnum status) {
    return status != null ? status.code : null;
  }
}