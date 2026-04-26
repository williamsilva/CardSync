package com.cardsync.domain.model.enums;

import com.cardsync.domain.exception.BusinessException;
import com.cardsync.domain.exception.ErrorCode;
import lombok.Getter;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Getter
public enum PeriodEnum {

  NULL(0),
  DAY(1),
  END(2),
  YEAR(3),
  MONTH(4),
  START(5),
  INTERVAL(6);

  private final int code;

  PeriodEnum(int code) {
    this.code = code;
  }

  /*
   * Lookup O(1)
   */
  private static final Map<Integer, PeriodEnum> BY_CODE =
    Arrays.stream(values())
      .collect(Collectors.toUnmodifiableMap(PeriodEnum::getCode, Function.identity()));

  private static final Map<String, PeriodEnum> BY_NAME =
    Arrays.stream(values())
      .collect(Collectors.toUnmodifiableMap(Enum::name, Function.identity()));

  /*
   * Converte código do banco -> enum
   */
  public static PeriodEnum fromCode(Integer code) {

    if (code == null) {
      return null;
    }

    PeriodEnum value = BY_CODE.get(code);

    if (value == null) {
      throw BusinessException.badRequest(
        ErrorCode.VALIDATION_ERROR,
        "Invalid PeriodEnum code: " + code
      );
    }

    return value;
  }

  /*
   * Converte string -> enum
   */
  public static PeriodEnum fromName(String name) {

    if (name == null || name.isBlank()) {
      return null;
    }

    PeriodEnum value = BY_NAME.get(name.trim().toUpperCase());

    if (value == null) {
      throw BusinessException.badRequest(
        ErrorCode.VALIDATION_ERROR,
        "Invalid PeriodEnum name: " + name
      );
    }

    return value;
  }

  /*
   * Enum -> código do banco
   */
  public static Integer toCode(PeriodEnum status) {
    return status != null ? status.code : null;
  }

}