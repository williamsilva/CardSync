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
@Table(name = "cs_settled_debt")
public class SettledDebtEntity extends AuditableEntityBase {

  private String tid;
  private Long nsu;
  private Integer pvNumber;
  private String recordType;
  private String cardNumber;
  private Integer lineNumber;
  private Integer reasonCode;
  private String compensation;
  private String authorization;
  private Long letterNumber;
  private LocalDate letterDate;
  private String referenceMonth;
  private String reasonDescription;
  private Integer pvNumberOriginal;
  private Integer numberRvOriginal;
  private LocalDate dateRvOriginal;
  private Long numberDebitOrder;
  private Integer codeCompensation;
  private LocalDate dateDebitOrder;
  private LocalDate liquidatedDate;
  private BigDecimal valueDebitOrder;
  private BigDecimal liquidatedValue;
  private Long numberProcessChargeback;
  private Long retentionProcessNumber;
  private LocalDate dateOriginalTransaction;
  private BigDecimal originalTransactionValue;
  private BigDecimal requestedCancellationValue;
  private Integer codeReasonAdjustment2;

  @ManyToOne(fetch = FetchType.LAZY)
  private FlagEntity flag;

  @ManyToOne(fetch = FetchType.LAZY)
  private AcquirerEntity acquirer;

  @ManyToOne(fetch = FetchType.LAZY)
  private ProcessedFileEntity processedFile;
}
