package com.cardsync.domain.model.enums;

import com.cardsync.domain.exception.BusinessException;
import com.cardsync.domain.exception.ErrorCode;
import lombok.Getter;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Getter
public enum TypeCompanyEnum {

  NULL(0),
  MATRIZ(1),
  FILIAL(2);

  private final int code;

  TypeCompanyEnum(int code) {
    this.code = code;
  }

  /*
   * Lookup O(1)
   */
  private static final Map<Integer, TypeCompanyEnum> BY_CODE =
    Arrays.stream(values())
      .collect(Collectors.toUnmodifiableMap(TypeCompanyEnum::getCode, Function.identity()));

  private static final Map<String, TypeCompanyEnum> BY_NAME =
    Arrays.stream(values())
      .collect(Collectors.toUnmodifiableMap(Enum::name, Function.identity()));

  /*
   * Converte código do banco -> enum
   */
  public static TypeCompanyEnum fromCode(Integer code) {

    if (code == null) {
      return null;
    }

    TypeCompanyEnum value = BY_CODE.get(code);

    if (value == null) {
      throw BusinessException.badRequest(
        ErrorCode.VALIDATION_ERROR,
        "Invalid TypeCompanyEnum code: " + code
      );
    }

    return value;
  }

  /*
   * Converte string -> enum
   */
  public static TypeCompanyEnum fromName(String name) {

    if (name == null || name.isBlank()) {
      return null;
    }

    TypeCompanyEnum value = BY_NAME.get(name.trim().toUpperCase());

    if (value == null) {
      throw BusinessException.badRequest(
        ErrorCode.VALIDATION_ERROR,
        "Invalid TypeCompanyEnum name: " + name
      );
    }

    return value;
  }

  /*
   * Enum -> código do banco
   */
  public static Integer toCode(TypeCompanyEnum status) {
    return status != null ? status.code : null;
  }

}