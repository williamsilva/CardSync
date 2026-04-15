package com.cardsync.domain.model.enums;

import com.cardsync.domain.exception.BusinessException;
import com.cardsync.domain.exception.ErrorCode;
import lombok.Getter;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Getter
public enum ModalityEnum {

  NULL(0),
  CASH_DEBIT(1),
  CASH_CREDIT(2),
  INSTALLMENT_CREDIT_2_6(3),
  INSTALLMENT_CREDIT_7_12(4),
  INSTALLMENT_CREDIT_13_18(6),
  DIGITAL_WALLET(5),
  OUTROS(9);

  private final int code;

  ModalityEnum(int code) {
    this.code = code;
  }

  /*
   * Lookup O(1)
   */
  private static final Map<Integer, ModalityEnum> BY_CODE =
    Arrays.stream(values())
      .collect(Collectors.toUnmodifiableMap(ModalityEnum::getCode, Function.identity()));

  private static final Map<String, ModalityEnum> BY_NAME =
    Arrays.stream(values())
      .collect(Collectors.toUnmodifiableMap(Enum::name, Function.identity()));

  /*
   * Converte código do banco -> enum
   */
  public static ModalityEnum fromCode(Integer code) {

    if (code == null) {
      return null;
    }

    ModalityEnum value = BY_CODE.get(code);

    if (value == null) {
      throw BusinessException.badRequest(
        ErrorCode.VALIDATION_ERROR,
        "Invalid ModalityEnum code: " + code
      );
    }

    return value;
  }

  /*
   * Converte string -> enum
   */
  public static ModalityEnum fromName(String name) {

    if (name == null || name.isBlank()) {
      return null;
    }

    ModalityEnum value = BY_NAME.get(name.trim().toUpperCase());

    if (value == null) {
      throw BusinessException.badRequest(
        ErrorCode.VALIDATION_ERROR,
        "Invalid ModalityEnum name: " + name
      );
    }

    return value;
  }

  /*
   * Enum -> código do banco
   */
  public static Integer toCode(ModalityEnum status) {
    return status != null ? status.code : null;
  }

}