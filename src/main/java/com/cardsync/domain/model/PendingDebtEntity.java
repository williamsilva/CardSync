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
@Table(name = "cs_pending_debt")
public class PendingDebtEntity extends AuditableEntityBase {

  private String tid;
  private Long nsu;
  private Integer pvNumber;
  private String recordType;
  private String cardNumber;
  private Integer lineNumber;
  private Integer reasonCode;
  private String authorization;
  private Long letterNumber;
  private LocalDate letterDate;
  private String referenceMonth;
  private String reasonDescription;
  private Integer pvNumberOriginal;
  private Integer numberRvOriginal;
  private LocalDate dateRvOriginal;
  private Long numberDebitOrder;
  private LocalDate dateDebitOrder;
  private BigDecimal valueDebitOrder;
  private BigDecimal compensatedValue;
  private LocalDate paymentDate;
  private BigDecimal pendingValue;
  private Long retentionProcessNumber;
  private Integer compensationCode;
  private String compensationDescription;
  private Integer reasonCode2;
  private Long numberProcessChargeback;
  private LocalDate dateOriginalTransaction;
  private BigDecimal originalTransactionValue;

  @ManyToOne(fetch = FetchType.LAZY)
  private FlagEntity flag;

  @ManyToOne(fetch = FetchType.LAZY)
  private AcquirerEntity acquirer;

  @ManyToOne(fetch = FetchType.LAZY)
  private CompanyEntity company;

  @ManyToOne(fetch = FetchType.LAZY)
  private EstablishmentEntity establishment;

  @ManyToOne(fetch = FetchType.LAZY)
  private ProcessedFileEntity processedFile;
}
