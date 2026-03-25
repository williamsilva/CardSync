package com.cardsync.domain.model.enums;

import com.cardsync.domain.exception.BusinessException;
import com.cardsync.domain.exception.ErrorCode;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.Getter;

@Getter
public enum EmailLogStatusEnum {

  NULL(0),
  SENT(1),
  FAILED(2);

  private final int code;

  EmailLogStatusEnum(int code) {
    this.code = code;
  }

  /*
   * Lookup O(1)
   */
  private static final Map<Integer, EmailLogStatusEnum> BY_CODE =
    Arrays.stream(values())
      .collect(Collectors.toUnmodifiableMap(EmailLogStatusEnum::getCode, Function.identity()));

  private static final Map<String, EmailLogStatusEnum> BY_NAME =
    Arrays.stream(values())
      .collect(Collectors.toUnmodifiableMap(Enum::name, Function.identity()));

  /*
   * Converte código do banco -> enum
   */
  public static EmailLogStatusEnum fromCode(Integer code) {

    if (code == null) {
      return null;
    }

    EmailLogStatusEnum value = BY_CODE.get(code);

    if (value == null) {
      throw BusinessException.badRequest(
        ErrorCode.VALIDATION_ERROR,
        "Invalid EmailLogStatusEnum code: " + code
      );
    }

    return value;
  }

  /*
   * Converte string -> enum
   */
  public static EmailLogStatusEnum fromName(String name) {

    if (name == null || name.isBlank()) {
      return null;
    }

    EmailLogStatusEnum value = BY_NAME.get(name.trim().toUpperCase());

    if (value == null) {
      throw BusinessException.badRequest(
        ErrorCode.VALIDATION_ERROR,
        "Invalid EmailLogStatusEnum name: " + name
      );
    }

    return value;
  }

  /*
   * Enum -> código do banco
   */
  public static Integer toCode(EmailLogStatusEnum status) {
    return status != null ? status.code : null;
  }

}