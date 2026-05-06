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
@Table(name = "cs_credit_totalizer")
public class CreditTotalizerEntity extends AuditableEntityBase {

  private Integer pvNumber;
  private String recordType;
  private Integer lineNumber;
  private LocalDate creditDate;
  private BigDecimal totalCreditValue;
  private LocalDate advanceCreditDate;
  private LocalDate fileGenerationDate;
  private BigDecimal totalValueAdvanceCredits;

  @ManyToOne(fetch = FetchType.LAZY)
  private AcquirerEntity acquirer;

  @ManyToOne(fetch = FetchType.LAZY)
  private CompanyEntity company;

  @ManyToOne(fetch = FetchType.LAZY)
  private BankingDomicileEntity bankingDomicile;

  @ManyToOne(fetch = FetchType.LAZY)
  private ProcessedFileEntity processedFile;
}
