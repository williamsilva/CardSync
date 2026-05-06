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
import java.time.LocalDate;

@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "cs_rede_negotiated_transaction")
public class RedeNegotiatedTransactionEntity extends AuditableEntityBase {

  private String recordType;
  private Integer lineNumber;
  private Integer establishmentNumber;
  private Integer rvNumber;
  private LocalDate saleDate;
  private LocalDate rvCreditDate;
  private String transactionType;
  private String flagCode;
  private Integer negotiationType;
  private Long settlementSummaryNumber;
  private LocalDate settlementSummaryDate;
  private BigDecimal settlementSummaryValue;
  private Long negotiationContractNumber;
  private String partnerCnpj;
  private Long generatedRlDocumentNumber;
  private BigDecimal negotiatedValue;
  private LocalDate negotiationDate;
  private LocalDate liquidationDate;
  private Integer bank;
  private Integer agency;
  private Long account;
  private Integer creditStatus;

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
