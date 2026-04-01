package com.cardsync.domain.model.enums;

import com.cardsync.domain.exception.BusinessException;
import com.cardsync.domain.exception.ErrorCode;
import lombok.Getter;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Getter
public enum TypeEstablishmentEnum {

  NULL(0),
  PDV_TEF(1),
  ECOMMERCE(2);

  private final int code;

  TypeEstablishmentEnum(int code) {
    this.code = code;
  }

  /*
   * Lookup O(1)
   */
  private static final Map<Integer, TypeEstablishmentEnum> BY_CODE =
    Arrays.stream(values())
      .collect(Collectors.toUnmodifiableMap(TypeEstablishmentEnum::getCode, Function.identity()));

  private static final Map<String, TypeEstablishmentEnum> BY_NAME =
    Arrays.stream(values())
      .collect(Collectors.toUnmodifiableMap(Enum::name, Function.identity()));

  /*
   * Converte código do banco -> enum
   */
  public static TypeEstablishmentEnum fromCode(Integer code) {

    if (code == null) {
      return null;
    }

    TypeEstablishmentEnum value = BY_CODE.get(code);

    if (value == null) {
      throw BusinessException.badRequest(
        ErrorCode.VALIDATION_ERROR,
        "Invalid TypeEstablishmentEnum code: " + code
      );
    }

    return value;
  }

  /*
   * Converte string -> enum
   */
  public static TypeEstablishmentEnum fromName(String name) {

    if (name == null || name.isBlank()) {
      return null;
    }

    TypeEstablishmentEnum value = BY_NAME.get(name.trim().toUpperCase());

    if (value == null) {
      throw BusinessException.badRequest(
        ErrorCode.VALIDATION_ERROR,
        "Invalid TypeEstablishmentEnum name: " + name
      );
    }

    return value;
  }

  /*
   * Enum -> código do banco
   */
  public static Integer toCode(TypeEstablishmentEnum status) {
    return status != null ? status.code : null;
  }

}