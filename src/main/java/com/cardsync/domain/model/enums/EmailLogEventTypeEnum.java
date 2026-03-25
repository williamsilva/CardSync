package com.cardsync.domain.model.enums;

import com.cardsync.domain.exception.BusinessException;
import com.cardsync.domain.exception.ErrorCode;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.Getter;

@Getter
public enum EmailLogEventTypeEnum {

  NULL(0),
  PASSWORD_RESET(1),
  FIRST_PASSWORD(2);

  private final int code;

  EmailLogEventTypeEnum(int code) {
    this.code = code;
  }

  /*
   * Lookup O(1)
   */
  private static final Map<Integer, EmailLogEventTypeEnum> BY_CODE =
    Arrays.stream(values())
      .collect(Collectors.toUnmodifiableMap(EmailLogEventTypeEnum::getCode, Function.identity()));

  private static final Map<String, EmailLogEventTypeEnum> BY_NAME =
    Arrays.stream(values())
      .collect(Collectors.toUnmodifiableMap(Enum::name, Function.identity()));

  /*
   * Converte código do banco -> enum
   */
  public static EmailLogEventTypeEnum fromCode(Integer code) {

    if (code == null) {
      return null;
    }

    EmailLogEventTypeEnum value = BY_CODE.get(code);

    if (value == null) {
      throw BusinessException.badRequest(
        ErrorCode.VALIDATION_ERROR,
        "Invalid EmailLogEventTypeEnum code: " + code
      );
    }

    return value;
  }

  /*
   * Converte string -> enum
   */
  public static EmailLogEventTypeEnum fromName(String name) {

    if (name == null || name.isBlank()) {
      return null;
    }

    EmailLogEventTypeEnum value = BY_NAME.get(name.trim().toUpperCase());

    if (value == null) {
      throw BusinessException.badRequest(
        ErrorCode.VALIDATION_ERROR,
        "Invalid EmailLogEventTypeEnum name: " + name
      );
    }

    return value;
  }

  /*
   * Enum -> código do banco
   */
  public static Integer toCode(EmailLogEventTypeEnum status) {
    return status != null ? status.code : null;
  }

}