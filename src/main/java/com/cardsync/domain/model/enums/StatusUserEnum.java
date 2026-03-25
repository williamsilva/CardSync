package com.cardsync.domain.model.enums;

import com.cardsync.domain.exception.BusinessException;
import com.cardsync.domain.exception.ErrorCode;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.Getter;

@Getter
public enum StatusUserEnum {

  NULL(0),
  ACTIVE(1),
  INACTIVE(2),
  BLOCKED(3),
  DISABLED(4),
  PENDING_PASSWORD(5);

  private final int code;

  StatusUserEnum(int code) {
    this.code = code;
  }

  /*
   * Lookup O(1)
   */
  private static final Map<Integer, StatusUserEnum> BY_CODE =
    Arrays.stream(values())
      .collect(Collectors.toUnmodifiableMap(StatusUserEnum::getCode, Function.identity()));

  private static final Map<String, StatusUserEnum> BY_NAME =
    Arrays.stream(values())
      .collect(Collectors.toUnmodifiableMap(Enum::name, Function.identity()));

  /*
   * Converte código do banco -> enum
   */
  public static StatusUserEnum fromCode(Integer code) {

    if (code == null) {
      return null;
    }

    StatusUserEnum value = BY_CODE.get(code);

    if (value == null) {
      throw BusinessException.badRequest(
        ErrorCode.VALIDATION_ERROR,
        "Invalid StatusUserEnum code: " + code
      );
    }

    return value;
  }

  /*
   * Converte string -> enum
   */
  public static StatusUserEnum fromName(String name) {

    if (name == null || name.isBlank()) {
      return null;
    }

    StatusUserEnum value = BY_NAME.get(name.trim().toUpperCase());

    if (value == null) {
      throw BusinessException.badRequest(
        ErrorCode.VALIDATION_ERROR,
        "Invalid StatusUserEnum name: " + name
      );
    }

    return value;
  }

  /*
   * Enum -> código do banco
   */
  public static Integer toCode(StatusUserEnum status) {
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

  public boolean isPendingPassword() {
    return this == PENDING_PASSWORD;
  }

  public boolean canLogin() {
    return this == ACTIVE;
  }

}