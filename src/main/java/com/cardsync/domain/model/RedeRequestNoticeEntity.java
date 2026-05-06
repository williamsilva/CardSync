package com.cardsync.domain.model;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "cs_rede_request_notice")
public class RedeRequestNoticeEntity extends AuditableEntityBase {

  private Integer lineNumber;
  private String recordType;
  private Integer pvNumber;
  private Integer rvNumber;
  private String cardNumber;
  private BigDecimal transactionValue;
  private LocalDate saleDate;
  private BigInteger referenceNumber;
  private BigInteger processNumber;
  private Long nsu;
  private String authorization;
  private Integer requestCode;
  private LocalDate deadline;
  private Integer requestStatus;
  private String tid;
  private String ecommerceOrderNumber;

  @ManyToOne(fetch = FetchType.LAZY)
  private FlagEntity flag;

  @ManyToOne(fetch = FetchType.LAZY)
  private AcquirerEntity acquirer;

  @ManyToOne(fetch = FetchType.LAZY)
  private CompanyEntity company;

  @ManyToOne(fetch = FetchType.LAZY)
  private EstablishmentEntity establishment;

  @ManyToOne(fetch = FetchType.LAZY)
  private SalesSummaryEntity salesSummary;

  @ManyToOne(fetch = FetchType.LAZY)
  private ProcessedFileEntity processedFile;
}
