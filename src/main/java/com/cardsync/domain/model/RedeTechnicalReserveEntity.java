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
@Table(name = "cs_rede_technical_reserve")
public class RedeTechnicalReserveEntity extends AuditableEntityBase {

  private String recordType;
  private Integer lineNumber;
  private Integer pvNumber;
  private Integer rvNumberOriginal;
  private LocalDate rvDateOriginal;
  private String flagCode;
  private Integer installmentNumber;
  private LocalDate dueDate;
  private Long creditOrderNumber;
  private Long creditOrderReferenceNumber;
  private BigDecimal creditOrderValue;
  private LocalDate reserveInclusionDate;
  private LocalDate reserveExclusionDate;
  private Integer bank;
  private Integer agency;
  private Long account;
  private Integer reserveStatus;

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
