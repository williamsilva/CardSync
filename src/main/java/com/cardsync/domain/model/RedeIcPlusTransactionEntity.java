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
@Table(name = "cs_rede_ic_plus_transaction")
public class RedeIcPlusTransactionEntity extends AuditableEntityBase {

  private String recordType;
  private Integer lineNumber;
  private Integer pvNumber;
  private Integer rvNumberOriginal;
  private LocalDate rvDateOriginal;
  private Long nsu;
  private LocalDate transactionDate;
  private Integer mcc;
  private BigDecimal interchangeValue;
  private BigDecimal plusValue;
  private String entryMode;
  private String cardProfile;

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
