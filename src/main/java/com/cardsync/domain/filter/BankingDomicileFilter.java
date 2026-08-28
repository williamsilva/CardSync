package com.cardsync.domain.filter;

import java.util.List;
import java.util.UUID;

public record BankingDomicileFilter(
  UUID id,
  List<String> banks,
  List<String> companies,
  Boolean active
) {
}
