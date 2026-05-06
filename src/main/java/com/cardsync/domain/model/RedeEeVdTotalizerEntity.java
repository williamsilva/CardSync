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

@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "cs_rede_eevd_totalizer")
public class RedeEeVdTotalizerEntity extends AuditableEntityBase {

  private String recordType;
  private Integer lineNumber;
  private Integer pvNumber;
  private Integer matrixNumber;
  private Integer salesSummaryQuantity;
  private Integer salesReceiptQuantity;
  private BigDecimal totalGrossValue;
  private BigDecimal totalDiscountValue;
  private BigDecimal totalLiquidValue;
  private BigDecimal predatingGrossValue;
  private BigDecimal predatingDiscountValue;
  private BigDecimal predatingLiquidValue;
  private Integer totalFileRecords;

  @ManyToOne(fetch = FetchType.LAZY)
  private AcquirerEntity acquirer;

  @ManyToOne(fetch = FetchType.LAZY)
  private CompanyEntity company;

  @ManyToOne(fetch = FetchType.LAZY)
  private EstablishmentEntity establishment;

  @ManyToOne(fetch = FetchType.LAZY)
  private ProcessedFileEntity processedFile;
}
