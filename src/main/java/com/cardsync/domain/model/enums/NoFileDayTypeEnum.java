package com.cardsync.domain.model.enums;

import com.cardsync.domain.exception.BusinessException;
import com.cardsync.domain.exception.ErrorCode;
import lombok.Getter;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Tipo de dia sem envio de arquivos (cs_no_file_day).
 * Permite distinguir o motivo de não ter havido arquivos no dia.
 */
@Getter
public enum NoFileDayTypeEnum {

  NULL(0),
  NO_MOVEMENT(1),
  HOLIDAY(2),
  SYSTEM_OUTAGE(3),
  OTHER(4);

  private final int code;

  NoFileDayTypeEnum(int code) {
    this.code = code;
  }

  private static final Map<Integer, NoFileDayTypeEnum> BY_CODE =
    Arrays.stream(values())
      .collect(Collectors.toUnmodifiableMap(NoFileDayTypeEnum::getCode, Function.identity()));

  private static final Map<String, NoFileDayTypeEnum> BY_NAME =
    Arrays.stream(values())
      .collect(Collectors.toUnmodifiableMap(Enum::name, Function.identity()));

  public static NoFileDayTypeEnum fromCode(Integer code) {
    if (code == null) {
      return null;
    }
    NoFileDayTypeEnum value = BY_CODE.get(code);
    if (value == null) {
      throw BusinessException.badRequest(
        ErrorCode.VALIDATION_ERROR,
        "Invalid NoFileDayTypeEnum code: " + code
      );
    }
    return value;
  }

  public static NoFileDayTypeEnum fromName(String name) {
    if (name == null || name.isBlank()) {
      return null;
    }
    NoFileDayTypeEnum value = BY_NAME.get(name.trim().toUpperCase());
    if (value == null) {
      throw BusinessException.badRequest(
        ErrorCode.VALIDATION_ERROR,
        "Invalid NoFileDayTypeEnum name: " + name
      );
    }
    return value;
  }
}