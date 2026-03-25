package com.cardsync.api.exceptionhandler;

import com.cardsync.core.web.CorrelationIdFilter;
import com.cardsync.domain.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
@RequiredArgsConstructor
public class ApiExceptionHandler {

  private final Clock clock;
  private final MessageSource messages;

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleValidation(
    MethodArgumentNotValidException ex, HttpServletRequest req) {
    Locale locale = req.getLocale();

    List<FieldErrorResponse> fields = ex.getBindingResult()
      .getFieldErrors()
      .stream()
      .map(fe -> new FieldErrorResponse(
        fe.getField(),
        normalizeValidationCode(fe),
        resolveValidationMessage(fe, locale),
        fe.getDefaultMessage(),
        safeRejectedValue(fe.getRejectedValue())
      ))
      .toList();

    return build(
      req,
      HttpStatus.BAD_REQUEST,
      "VALIDATION_ERROR",
      "VALIDATION_ERROR",
      msg("error.validation", locale),
      "Validation failed for request body",
      fields
    );
  }

  @ExceptionHandler(ApiBusinessException.class)
  public ResponseEntity<ErrorResponse> handleApiBusiness(
    ApiBusinessException ex, HttpServletRequest req) {
    Locale locale = req.getLocale();

    String userMessage = messages.getMessage(
      ex.getMessageKey(),
      ex.getMessageArgs(),
      ex.getCode(),
      locale
    );

    return build(
      req,
      ex.getStatus(),
      mapErrorName(ex.getStatus()),
      ex.getCode(),
      userMessage,
      ex.getTechnicalMessage(),
      List.of()
    );
  }

  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ErrorResponse> handleBusiness(
    BusinessException ex, HttpServletRequest req) {
    Locale locale = req.getLocale();

    String userMessage = messages.getMessage(
      ex.getMessageKey(),
      ex.getArgs(),
      ex.getCode(),
      locale
    );

    return build(
      req,
      ex.getStatus(),
      mapErrorName(ex.getStatus()),
      ex.getCode(),
      userMessage,
      ex.getTechnicalMessage(),
      List.of()
    );
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ErrorResponse> handleIllegalArg(
    IllegalArgumentException ex, HttpServletRequest req) {
    Locale locale = req.getLocale();

    return build(
      req,
      HttpStatus.BAD_REQUEST,
      "BUSINESS_ERROR",
      "INVALID_ARGUMENT",
      ex.getMessage() != null ? ex.getMessage() : msg("error.business", locale),
      ex.getMessage(),
      List.of()
    );
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ErrorResponse> handleDataIntegrity(
    DataIntegrityViolationException ex, HttpServletRequest req) {
    Locale locale = req.getLocale();

    ex.getMostSpecificCause();
    String raw = ex.getMostSpecificCause().getMessage();

    return build(
      req,
      HttpStatus.CONFLICT,
      "BUSINESS_ERROR",
      "BUSINESS_ERROR",
      msg("error.business", locale),
      raw,
      List.of()
    );
  }

  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<?> handleNoResourceFound(
    NoResourceFoundException ex, HttpServletRequest req) {
    String accept = req.getHeader(HttpHeaders.ACCEPT);
    if (accept != null && accept.contains(MediaType.TEXT_HTML_VALUE)) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    Locale locale = req.getLocale();
    return build(
      req,
      HttpStatus.NOT_FOUND,
      "NOT_FOUND",
      "NOT_FOUND",
      msg("error.NOT_FOUND", locale),
      ex.getMessage(),
      List.of()
    );
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ErrorResponse> handleAccessDenied(
    AccessDeniedException ex, HttpServletRequest req) {
    Locale locale = req.getLocale();

    return build(
      req,
      HttpStatus.FORBIDDEN,
      "ACCESS_DENIED",
      "ACCESS_DENIED",
      msg("error.ACCESS_DENIED", locale),
      ex.getMessage() != null ? ex.getMessage() : "Access denied",
      List.of()
    );
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleGeneric(
    Exception ex, HttpServletRequest req) {
    Locale locale = req.getLocale();

    return build(
      req,
      HttpStatus.INTERNAL_SERVER_ERROR,
      "INTERNAL_ERROR",
      "INTERNAL_ERROR",
      msg("error.internal", locale),
      ex.getMessage(),
      List.of()
    );
  }

  private ResponseEntity<ErrorResponse> build(
    HttpServletRequest req, HttpStatus status, String error, String code,
    String userMessage, String technicalMessage, List<FieldErrorResponse> fields) {
    Object cid = req.getAttribute(CorrelationIdFilter.ATTR);
    String correlationId = cid != null ? cid.toString() : null;

    ErrorResponse body = new ErrorResponse(
      OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC),
      status.value(),
      error,
      code,
      userMessage,
      technicalMessage,
      fields,
      correlationId,
      req.getRequestURI(),
      req.getMethod()
    );

    return ResponseEntity.status(status)
      .contentType(MediaType.APPLICATION_JSON)
      .body(body);
  }

  private String msg(String key, Locale locale) {
    return messages.getMessage(key, null, key, locale);
  }

  private String normalizeValidationCode(FieldError fe) {
    String c = fe.getCode();
    if (c == null) {
      return "invalid";
    }

    return switch (c) {
      case "NotNull", "NotBlank", "NotEmpty" -> "required";
      case "Email" -> "email";
      case "Pattern" -> "pattern";
      case "Size" -> "size";
      case "Min", "DecimalMin" -> "min";
      case "Max", "DecimalMax" -> "max";
      default -> "invalid";
    };
  }

  private String resolveValidationMessage(FieldError fe, Locale locale) {
    String key = fe.getDefaultMessage();
    if (key == null || key.isBlank()) {
      return msg("error.validation", locale);
    }
    return messages.getMessage(key, null, key, locale);
  }

  private Object safeRejectedValue(Object value) {
    if (value == null) {
      return null;
    }

    String s = String.valueOf(value);
    return s.length() > 120 ? s.substring(0, 120) + "..." : s;
  }

  private String mapErrorName(HttpStatus status) {
    if (status == null) return "BUSINESS_ERROR";

    return switch (status) {
      case NOT_FOUND -> "NOT_FOUND";
      case FORBIDDEN -> "ACCESS_DENIED";
      default -> "BUSINESS_ERROR";
    };
  }
}