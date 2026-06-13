package com.cardsync.domain.filter;

import java.util.List;
import java.util.UUID;

public record SaleSummaryFilter(
  UUID id,
  List<String> flags,
  List<String> companies,
  List<String> acquirers,
  List<String> establishments
) {
}
