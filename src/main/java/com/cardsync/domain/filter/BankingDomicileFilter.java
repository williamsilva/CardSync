package com.cardsync.domain.filter;

import java.util.UUID;

public record BankingDomicileFilter(
  UUID id,
  Integer agency,
  Integer currentAccount,
  String holderDocument,
  String holderName,
  Boolean active,
  UUID bankId,
  UUID companyId,
  UUID establishmentId
) {
}