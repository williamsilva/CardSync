package com.cardsync.bff.controller.v1.representation.model.conciliation;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AcquirerSaleAnalysisModel(
  UUID id,
  OffsetDateTime saleDate,
  String company,
  String establishment,
  String acquirer,
  String flag,
  String modality,
  Long nsu,
  String authorization,
  String tid,
  Integer rvNumber,
  BigDecimal grossValue,
  BigDecimal discountValue,
  BigDecimal liquidValue,
  Integer installmentNumber,
  Integer installmentTotal,
  String transactionStatus,
  String reconciliationStatus,
  String statusPaymentBank,
  String processedFile
) {}
