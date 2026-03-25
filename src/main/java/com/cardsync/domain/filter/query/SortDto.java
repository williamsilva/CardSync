package com.cardsync.domain.filter.query;

public record SortDto(
  String field,
  Integer order // 1 or -1
) {}
