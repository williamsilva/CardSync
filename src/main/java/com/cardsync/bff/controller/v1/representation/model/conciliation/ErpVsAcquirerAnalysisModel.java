package com.cardsync.bff.controller.v1.representation.model.conciliation;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ErpVsAcquirerAnalysisModel(
  UUID id,
  OffsetDateTime saleDateErp,
  OffsetDateTime saleDateAcquirer,
  String company,
  String establishment,
  String acquirer,
  String flagErp,
  String flagAcquirer,
  String modalityErp,
  String modalityAcquirer,
  Long nsuErp,
  Long nsuAcquirer,
  String authorizationErp,
  String authorizationAcquirer,
  BigDecimal erpGrossValue,
  BigDecimal acquirerGrossValue,
  BigDecimal differenceValue,
  Integer installmentErp,
  Integer installmentAcquirer,
  String status
) {}
