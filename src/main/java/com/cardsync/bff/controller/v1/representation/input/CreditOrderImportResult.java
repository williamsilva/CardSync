package com.cardsync.bff.controller.v1.representation.input;

import java.util.List;
import java.util.UUID;

public record CreditOrderImportResult(
  int analyzed,
  int created,
  int skipped,
  List<UUID> createdIds,
  List<CreditOrderImportSkipReason> skippedReasons
) {}
