package com.cardsync.domain.filter.query;

public record RangeValue<T>(
  T from,
  T to
) {}
