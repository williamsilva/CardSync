package com.cardsync.api.exceptionhandler;

public record FieldErrorResponse(
  String field,
  String code,
  String userMessage,
  String technicalMessage,
  Object rejectedValue
) {}