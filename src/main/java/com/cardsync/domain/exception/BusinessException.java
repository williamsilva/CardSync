package com.cardsync.domain.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class BusinessException extends RuntimeException {

  private final String code;
  private final String messageKey;
  private final Object[] args;
  private final HttpStatus status;
  private final String technicalMessage;

  public BusinessException(
    HttpStatus status,
    String code,
    String messageKey,
    String technicalMessage,
    Object... args
  ) {
    super(technicalMessage != null ? technicalMessage : code);
    this.status = status != null ? status : HttpStatus.BAD_REQUEST;
    this.code = code;
    this.messageKey = messageKey;
    this.args = args != null ? args : new Object[0];
    this.technicalMessage = technicalMessage;
  }

  public static BusinessException badRequest(
    ErrorCode errorCode,
    String technicalMessage,
    Object... args
  ) {
    return new BusinessException(
      HttpStatus.BAD_REQUEST,
      errorCode.code(),
      errorCode.messageKey(),
      technicalMessage,
      args
    );
  }

  public static BusinessException forbidden(
    ErrorCode errorCode,
    String technicalMessage,
    Object... args
  ) {
    return new BusinessException(
      HttpStatus.FORBIDDEN,
      errorCode.code(),
      errorCode.messageKey(),
      technicalMessage,
      args
    );
  }

  public static BusinessException notFound(
    ErrorCode errorCode,
    String technicalMessage,
    Object... args
  ) {
    return new BusinessException(
      HttpStatus.NOT_FOUND,
      errorCode.code(),
      errorCode.messageKey(),
      technicalMessage,
      args
    );
  }

  public static BusinessException conflict(
    ErrorCode errorCode,
    String technicalMessage,
    Object... args
  ) {
    return new BusinessException(
      HttpStatus.CONFLICT,
      errorCode.code(),
      errorCode.messageKey(),
      technicalMessage,
      args
    );
  }

}