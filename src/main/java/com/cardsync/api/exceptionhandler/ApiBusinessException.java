package com.cardsync.api.exceptionhandler;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class ApiBusinessException extends RuntimeException {

  private final String code;
  private final HttpStatus status;
  private final String messageKey;
  private final Object[] messageArgs;
  private final String technicalMessage;

  public ApiBusinessException(
    HttpStatus status,
    String code,
    String messageKey,
    String technicalMessage,
    Object... messageArgs
  ) {
    super(technicalMessage != null ? technicalMessage : code);
    this.status = status;
    this.code = code;
    this.messageKey = messageKey;
    this.messageArgs = messageArgs;
    this.technicalMessage = technicalMessage;
  }

  public ApiBusinessException(
    HttpStatus status,
    String code,
    String messageKey,
    Object... messageArgs
  ) {
    this(status, code, messageKey, null, messageArgs);
  }

  public static ApiBusinessException badRequest(
    String code,
    String messageKey,
    Object... args
  ) {
    return new ApiBusinessException(HttpStatus.BAD_REQUEST, code, messageKey, null, args);
  }

  public static ApiBusinessException badRequestTechnical(
    String code,
    String messageKey,
    String technicalMessage,
    Object... args
  ) {
    return new ApiBusinessException(HttpStatus.BAD_REQUEST, code, messageKey, technicalMessage, args);
  }

  public static ApiBusinessException notFound(
    String code,
    String messageKey,
    Object... args
  ) {
    return new ApiBusinessException(HttpStatus.NOT_FOUND, code, messageKey, null, args);
  }

  public static ApiBusinessException forbidden(
    String code,
    String messageKey,
    Object... args
  ) {
    return new ApiBusinessException(HttpStatus.FORBIDDEN, code, messageKey, null, args);
  }

}