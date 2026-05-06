package com.cardsync.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "cs_totalizer_matrix")
public class TotalizerMatrixEntity extends AuditableEntityBase {

  private Integer pvNumber;
  private Integer lineNumber;
  private String recordType;
  private Integer totalNumberMatrixSummaries;
  private BigDecimal totalValueNormalCredits;
  private Integer valueAdvanceCredits;
  private BigDecimal totalValueAnticipated;
  private Integer amountCreditAdjustments;
  private BigDecimal totalValueCreditAdjustments;
  private Integer amountDebitAdjustments;
  private BigDecimal totalValueDebitAdjustments;
  private BigDecimal totalGrossValue;
  private Integer rejectedCvNsuQuantity;
  private BigDecimal totalRejectedValue;
  private BigDecimal totalRotatingValue;
  private BigDecimal totalInstallmentValue;
  private BigDecimal totalIataValue;
  private BigDecimal totalDollarValue;
  private BigDecimal totalDiscountValue;
  private BigDecimal totalLiquidValue;
  private BigDecimal totalTipValue;
  private BigDecimal totalBoardingFeeValue;
  private Integer acceptedCvNsuQuantity;

  @ManyToOne(fetch = FetchType.LAZY)
  private AcquirerEntity acquirer;

  @ManyToOne(fetch = FetchType.LAZY)
  private CompanyEntity company;

  @ManyToOne(fetch = FetchType.LAZY)
  private EstablishmentEntity establishment;

  @ManyToOne(fetch = FetchType.LAZY)
  private ProcessedFileEntity processedFile;
}
