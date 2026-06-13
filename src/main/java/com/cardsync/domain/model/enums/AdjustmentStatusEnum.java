package com.cardsync.domain.model.enums;

import com.cardsync.domain.exception.BusinessException;
import com.cardsync.domain.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Getter
@AllArgsConstructor
public enum AdjustmentStatusEnum {

  NULL(0),
  PENDING(1),
  ADJUSTED(2),
  ANALYSIS(3),
  FAVORED_CLIENT(4),
  FAVORED_COMPANY(5),
  NOT_LOCATED_ERP_ACQ(6);

  private final int code;

  /*
   * Lookup O(1)
   */
  private static final Map<Integer, AdjustmentStatusEnum> BY_CODE =
    Arrays.stream(values())
      .collect(Collectors.toUnmodifiableMap(AdjustmentStatusEnum::getCode, Function.identity()));

  private static final Map<String, AdjustmentStatusEnum> BY_NAME =
    Arrays.stream(values())
      .collect(Collectors.toUnmodifiableMap(Enum::name, Function.identity()));

  /*
   * Converte código do banco -> enum
   */
  public static AdjustmentStatusEnum fromCode(Integer code) {

    if (code == null) {
      return null;
    }

    AdjustmentStatusEnum value = BY_CODE.get(code);

    if (value == null) {
      throw BusinessException.badRequest(
        ErrorCode.VALIDATION_ERROR,
        "Invalid AdjustmentStatusEnum code: " + code
      );
    }

    return value;
  }

  /*
   * Converte string -> enum
   */
  public static AdjustmentStatusEnum fromName(String name) {

    if (name == null || name.isBlank()) {
      return null;
    }

    AdjustmentStatusEnum value = BY_NAME.get(name.trim().toUpperCase());

    if (value == null) {
      throw BusinessException.badRequest(
        ErrorCode.VALIDATION_ERROR,
        "Invalid AdjustmentStatusEnum name: " + name
      );
    }

    return value;
  }

  /*
   * Enum -> código do banco
   */
  public static Integer toCode(AdjustmentStatusEnum status) {
    return status != null ? status.code : null;
  }
}