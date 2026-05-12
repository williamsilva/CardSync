package com.cardsync.domain.model.enums;

import com.cardsync.domain.exception.BusinessException;
import com.cardsync.domain.exception.ErrorCode;
import lombok.Getter;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Getter
public enum StatusTransactionReasonEnum {

  NULL(0),
  CV_NOT_FOUND_ERP(1),
  CV_NOT_FOUND_ADQ(2),
  FLAG_MISMATCH(3),
  DIFFERENT_PLANS(4),
  SCHEDULED(5),
  CANCEL_VENDAS(6),
  CHARGEBACK(7),
  VALUE_MISMATCH(8),
  ACQUIRER_MISMATCH(9),
  AMBIGUOUS_MATCH(10);

  private final int code;

  StatusTransactionReasonEnum(int code) {
    this.code = code;
  }

  /*
   * Lookup O(1)
   */
  private static final Map<Integer, StatusTransactionReasonEnum> BY_CODE =
    Arrays.stream(values())
      .collect(Collectors.toUnmodifiableMap(StatusTransactionReasonEnum::getCode, Function.identity()));

  private static final Map<String, StatusTransactionReasonEnum> BY_NAME =
    Arrays.stream(values())
      .collect(Collectors.toUnmodifiableMap(Enum::name, Function.identity()));

  /*
   * Converte código do banco -> enum
   */
  public static StatusTransactionReasonEnum fromCode(Integer code) {

    if (code == null) {
      return null;
    }

    StatusTransactionReasonEnum value = BY_CODE.get(code);

    if (value == null) {
      throw BusinessException.badRequest(
        ErrorCode.VALIDATION_ERROR,
        "Invalid StatusTransactionReasonEnum code: " + code
      );
    }

    return value;
  }

  /*
   * Converte string -> enum
   */
  public static StatusTransactionReasonEnum fromName(String name) {

    if (name == null || name.isBlank()) {
      return null;
    }

    StatusTransactionReasonEnum value = BY_NAME.get(name.trim().toUpperCase());

    if (value == null) {
      throw BusinessException.badRequest(
        ErrorCode.VALIDATION_ERROR,
        "Invalid StatusTransactionReasonEnum name: " + name
      );
    }

    return value;
  }

  /*
   * Enum -> código do banco
   */
  public static Integer toCode(StatusTransactionReasonEnum status) {
    return status != null ? status.code : null;
  }

}