package com.cardsync.bff.controller.v1.representation.model.bank;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record BankReleaseModel(
  UUID id,
  String processedFile,
  Integer lineNumber,
  String bank,
  String bankCode,
  String company,
  String companyCnpj,
  String acquirer,
  String establishment,
  Integer pvNumber,
  String flag,
  String bankingDomicile,
  Integer agency,
  Integer currentAccount,
  LocalDate releaseDate,
  LocalDate accountingDate,
  BigDecimal releaseValue,
  Integer reconciliationStatus,
  Integer modalityPaymentBank,
  Integer historicalCodeBank,
  Integer releaseCategoryCode,
  String descriptionHistoricalBank,
  String documentComplementNumber,
  String complementRelease,
  Integer numberReconciliations,
  Integer numberCreditOrders,
  Integer numberParcels
) {
}