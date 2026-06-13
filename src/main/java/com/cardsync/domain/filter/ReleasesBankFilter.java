package com.cardsync.domain.filter;

import java.util.List;
import java.util.UUID;

public record ReleasesBankFilter(
  UUID id,

  List<String> flags,
  List<String> companies,
  List<String> acquirers
) {
}