package com.cardsync.domain.model.enums;

import com.cardsync.domain.exception.BusinessException;
import com.cardsync.domain.exception.ErrorCode;
import lombok.Getter;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Getter
public enum StatusEnum {

  NULL(0),
  ACTIVE(1),
  INACTIVE(2),
  BLOCKED(3);

  private final int code;

  StatusEnum(int code) {
    this.code = code;
  }

  /*
   * Lookup O(1)
   */
  private static final Map<Integer, StatusEnum> BY_CODE =
    Arrays.stream(values())
      .collect(Collectors.toUnmodifiableMap(StatusEnum::getCode, Function.identity()));

  private static final Map<String, StatusEnum> BY_NAME =
    Arrays.stream(values())
      .collect(Collectors.toUnmodifiableMap(Enum::name, Function.identity()));

  /*
   * Converte código do banco -> enum
   */
  public static StatusEnum fromCode(Integer code) {

    if (code == null) {
      return null;
    }

    StatusEnum value = BY_CODE.get(code);

    if (value == null) {
      throw BusinessException.badRequest(
        ErrorCode.VALIDATION_ERROR,
        "Invalid StatusEnum code: " + code
      );
    }

    return value;
  }

  /*
   * Converte string -> enum
   */
  public static StatusEnum fromName(String name) {

    if (name == null || name.isBlank()) {
      return null;
    }

    StatusEnum value = BY_NAME.get(name.trim().toUpperCase());

    if (value == null) {
      throw BusinessException.badRequest(
        ErrorCode.VALIDATION_ERROR,
        "Invalid StatusEnum name: " + name
      );
    }

    return value;
  }

  /*
   * Enum -> código do banco
   */
  public static Integer toCode(StatusEnum status) {
    return status != null ? status.code : null;
  }

  /*
   * Helpers de domínio
   */
  public boolean isActive() {
    return this == ACTIVE;
  }

  public boolean isInactive() {
    return this == INACTIVE;
  }

  public boolean isBlocked() {
    return this == BLOCKED;
  }

}