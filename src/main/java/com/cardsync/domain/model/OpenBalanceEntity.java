package com.cardsync.domain.model;

import jakarta.persistence.*;
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
@Table(name = "cs_open_balance")
public class OpenBalanceEntity extends AuditableEntityBase {

  private Integer pvNumber;
  private Integer rvNumber;
  private Integer lineNumber;
  private Integer numberOfReleases;
  private Integer settlementType;
  private Integer paymentStatus;

  private String launchType;
  private String openBalanceIndicator;

  private LocalDate paymentDate;
  private LocalDate originalDueDate;

  private BigDecimal grossValue;
  private BigDecimal liquidValue;

  @ManyToOne(fetch = FetchType.LAZY)
  private FlagEntity flag;

  @ManyToOne(fetch = FetchType.LAZY)
  private CompanyEntity company;

  @ManyToOne(fetch = FetchType.LAZY)
  private AcquirerEntity acquirer;

  @ManyToOne(fetch = FetchType.LAZY)
  private ProcessedFileEntity processedFile;

  @ManyToOne(fetch = FetchType.LAZY)
  private EstablishmentEntity establishment;

  @ManyToOne(fetch = FetchType.LAZY)
  private BankingDomicileEntity bankingDomicile;
}
