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
public class RequestNoticeEntity extends AuditableEntityBase {

  private Long nsu;
  private Integer pvNumber;
  private Integer rvNumber;
  private Integer lineNumber;
  private Integer requestCode;
  private Integer requestStatus;
  private BigInteger processNumber;
  private BigInteger referenceNumber;

  private String tid;
  private String recordType;
  private String cardNumber;
  private String authorization;
  private String ecommerceOrderNumber;

  private BigDecimal transactionValue;

  private LocalDate saleDate;
  private LocalDate deadline;

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
