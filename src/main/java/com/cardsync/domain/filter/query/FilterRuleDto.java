package com.cardsync.domain.filter.query;

public record FilterRuleDto(
  String matchMode, // "contains", "startsWith", "equals", ...
  Object value
) {}
