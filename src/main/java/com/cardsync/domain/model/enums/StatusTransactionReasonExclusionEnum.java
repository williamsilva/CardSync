package com.cardsync.domain.model.enums;

import com.cardsync.domain.exception.BusinessException;
import com.cardsync.domain.exception.ErrorCode;
import lombok.Getter;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Getter
public enum StatusTransactionReasonExclusionEnum {

  NULL(0),
  DUPLICITY(1),
  UNDONE(2),
  CV_NOT_FOUND_ADQ(3),
  CV_NOT_FOUND_ERP(4),
  INVALID_DATA(5),
  CANCELED(6),
  DELETED(7),
  TRANSACTION_ALREADY_CONCILIATED(8),
  OTHER(9);

  private final int code;

  StatusTransactionReasonExclusionEnum(int code) {
    this.code = code;
  }

  /*
   * Lookup O(1)
   */
  private static final Map<Integer, StatusTransactionReasonExclusionEnum> BY_CODE =
    Arrays.stream(values())
      .collect(Collectors.toUnmodifiableMap(StatusTransactionReasonExclusionEnum::getCode, Function.identity()));

  private static final Map<String, StatusTransactionReasonExclusionEnum> BY_NAME =
    Arrays.stream(values())
      .collect(Collectors.toUnmodifiableMap(Enum::name, Function.identity()));

  /*
   * Converte código do banco -> enum
   */
  public static StatusTransactionReasonExclusionEnum fromCode(Integer code) {

    if (code == null) {
      return null;
    }

    StatusTransactionReasonExclusionEnum value = BY_CODE.get(code);

    if (value == null) {
      throw BusinessException.badRequest(
        ErrorCode.VALIDATION_ERROR,
        "Invalid StatusTransactionReasonExclusionEnum code: " + code
      );
    }

    return value;
  }

  /*
   * Converte string -> enum
   */
  public static StatusTransactionReasonExclusionEnum fromName(String name) {

    if (name == null || name.isBlank()) {
      return null;
    }

    StatusTransactionReasonExclusionEnum value = BY_NAME.get(name.trim().toUpperCase());

    if (value == null) {
      throw BusinessException.badRequest(
        ErrorCode.VALIDATION_ERROR,
        "Invalid StatusTransactionReasonExclusionEnum name: " + name
      );
    }

    return value;
  }

  /*
   * Enum -> código do banco
   */
  public static Integer toCode(StatusTransactionReasonExclusionEnum status) {
    return status != null ? status.code : null;
  }

}