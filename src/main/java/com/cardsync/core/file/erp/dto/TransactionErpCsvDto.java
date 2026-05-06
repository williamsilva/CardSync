package com.cardsync.core.file.erp.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Getter
@Setter
public class TransactionErpCsvDto {
  private Integer lineNumber;
  private String transaction;
  private String origin;
  private String acquirer;
  private String modality;
  private String flag;
  private String installmentType;
  private Integer installment;
  private String cardNumber;
  private String cardName;
  private Long nsu;
  private String tid;
  private String authorization;
  private String threeDs;
  private String antiFraud;
  private BigDecimal grossValue;
  private OffsetDateTime saleDate;

  /**
   * Campos adicionais usados para resolver o contexto comercial do CardSync.
   * Nem todo layout ERP envia todos eles; o resolver usa a melhor combinação disponível.
   */
  private String companyCnpj;
  private String companyName;
  private Integer establishmentPvNumber;
  private String establishmentName;
  private String machine;
}
