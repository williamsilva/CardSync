package com.cardsync.domain.filter.query;

import java.util.List;
import java.util.Map;

public record ListQueryDto<TAdvanced>(
  int page,
  int size,
  List<SortDto> sort,
  Map<String, ColumnFilterDto> tableFilters,
  String globalFilter,
  TAdvanced advanced
) {}
