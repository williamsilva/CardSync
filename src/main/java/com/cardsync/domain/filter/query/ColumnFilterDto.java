package com.cardsync.domain.filter.query;

import java.util.List;

public record ColumnFilterDto(
  String operator,              // "and" | "or"
  List<FilterRuleDto> constraints
) {}
