package com.cardsync.api.exceptionhandler;

import java.time.OffsetDateTime;
import java.util.List;

public record ErrorResponse(
  OffsetDateTime timestamp,
  int status,
  String error,
  String code,
  String userMessage,
  String technicalMessage,
  List<FieldErrorResponse> fieldErrors,
  String correlationId,
  String path,
  String method
) {}