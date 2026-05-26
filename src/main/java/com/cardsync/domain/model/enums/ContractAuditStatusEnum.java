package com.cardsync.domain.model.enums;

import com.cardsync.domain.exception.BusinessException;
import com.cardsync.domain.exception.ErrorCode;
import lombok.Getter;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Getter
public enum ContractAuditStatusEnum {

  NULL(0),
  DIVERGENT_RATE(1),
  MISSING_VALID_CONTRACT(2),
  RESOLVED_WITH_ACQUIRER_RATE(3);

  private final int code;

  ContractAuditStatusEnum(int code) {
    this.code = code;
  }

  private static final Map<Integer, ContractAuditStatusEnum> BY_CODE =
    Arrays.stream(values())
      .collect(Collectors.toUnmodifiableMap(ContractAuditStatusEnum::getCode, Function.identity()));

  public static ContractAuditStatusEnum fromCode(Integer code) {
    if (code == null) return null;
    ContractAuditStatusEnum value = BY_CODE.get(code);
    if (value == null) {
      throw BusinessException.badRequest(
        ErrorCode.VALIDATION_ERROR,
        "Invalid ContractAuditStatusEnum code: " + code
      );
    }
    return value;
  }
}
