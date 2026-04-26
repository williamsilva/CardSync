package com.cardsync.domain.model.enums;

import com.cardsync.domain.exception.BusinessException;
import com.cardsync.domain.exception.ErrorCode;
import lombok.Getter;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Getter
public enum ContractEnum {

  NULL(0),
  VALIDITY(1),
  EXPIRED(2),
  CLOSED(3);

  private final int code;

  ContractEnum(int code) {
    this.code = code;
  }

  /*
   * Lookup O(1)
   */
  private static final Map<Integer, ContractEnum> BY_CODE =
    Arrays.stream(values())
      .collect(Collectors.toUnmodifiableMap(ContractEnum::getCode, Function.identity()));

  private static final Map<String, ContractEnum> BY_NAME =
    Arrays.stream(values())
      .collect(Collectors.toUnmodifiableMap(Enum::name, Function.identity()));

  /*
   * Converte código do banco -> enum
   */
  public static ContractEnum fromCode(Integer code) {

    if (code == null) {
      return null;
    }

    ContractEnum value = BY_CODE.get(code);

    if (value == null) {
      throw BusinessException.badRequest(
        ErrorCode.VALIDATION_ERROR,
        "Invalid ContractEnum code: " + code
      );
    }

    return value;
  }

  /*
   * Converte string -> enum
   */
  public static ContractEnum fromName(String name) {

    if (name == null || name.isBlank()) {
      return null;
    }

    ContractEnum value = BY_NAME.get(name.trim().toUpperCase());

    if (value == null) {
      throw BusinessException.badRequest(
        ErrorCode.VALIDATION_ERROR,
        "Invalid ContractEnum name: " + name
      );
    }

    return value;
  }

  /*
   * Enum -> código do banco
   */
  public static Integer toCode(ContractEnum status) {
    return status != null ? status.code : null;
  }

  /*
   * Helpers de domínio
   */
  public boolean isValidity() {
    return this == VALIDITY;
  }

  public boolean isExpired() {
    return this == EXPIRED;
  }

  public boolean isClosed() {
    return this == CLOSED;
  }
}