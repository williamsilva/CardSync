package com.cardsync.core.file.erp.validator;

import com.cardsync.domain.model.enums.ProcessedFileErrorTypeEnum;

public record TransactionErpValidationError(
  ProcessedFileErrorTypeEnum type,
  String code,
  String message
) {
  public static TransactionErpValidationError validation(String code, String message) {
    return new TransactionErpValidationError(ProcessedFileErrorTypeEnum.VALIDATION, code, message);
  }

  public static TransactionErpValidationError lookup(String code, String message) {
    return new TransactionErpValidationError(ProcessedFileErrorTypeEnum.LOOKUP, code, message);
  }

  public static TransactionErpValidationError contract(String code, String message) {
    return new TransactionErpValidationError(ProcessedFileErrorTypeEnum.CONTRACT, code, message);
  }
}
