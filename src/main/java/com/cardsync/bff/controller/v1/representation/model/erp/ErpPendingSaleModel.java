package com.cardsync.bff.controller.v1.representation.model.erp;

import com.cardsync.domain.model.enums.ErpCommercialStatusEnum;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ErpPendingSaleModel(
  UUID id,
  Integer lineNumber,
  String file,
  OffsetDateTime saleDate,
  Long nsu,
  String authorization,
  String acquirer,
  String flag,
  String company,
  String establishment,
  String sourceCompanyCnpj,
  String sourceCompanyName,
  Integer sourceEstablishmentPvNumber,
  String sourceEstablishmentName,
  Integer installment,
  BigDecimal grossValue,
  BigDecimal liquidValue,
  BigDecimal discountValue,
  BigDecimal contractedFee,
  ErpCommercialStatusEnum commercialStatus,
  String commercialStatusMessage
) {
}
