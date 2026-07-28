package com.cardsync.bff.controller.v1.representation.input;

import java.math.BigDecimal;
import java.util.List;

public record CreditOrderImportPreviewResult(
  List<String> fileNames,
  int analyzed,
  int wouldCreate,
  BigDecimal totalValue,
  int skipped,
  List<CreditOrderImportSkipReason> skippedReasons
) {}
