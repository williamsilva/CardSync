package com.cardsync.core.web.pagination;

public record PageMeta(
  int page,            // 0-based
  int size,
  long totalElements,
  int totalPages,
  boolean first,
  boolean last
) {}