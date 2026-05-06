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
@Table(name = "cs_installment_unscheduling")
public class InstallmentUnschedulingEntity extends AuditableEntityBase {

  private Long nsu;
  private String tid;
  private String orderNumber;
  private String recordType;
  private String cardNumber;
  private String typeDebit;
  private Integer lineNumber;
  private Integer numberInstallment;
  private Integer originalInstallmentNumber;
  private Integer adjustedInstallmentNumber;
  private Integer pvNumberOriginal;
  private Integer rvNumberOriginal;
  private Integer adjustedPvNumber;
  private Integer adjustedRvNumber;
  private Integer unschedulingStatus;
  private Integer negotiationType;
  private String referenceNumber;
  private Boolean ecommerce;
  private LocalDate dateCredit;
  private LocalDate adjustedCreditDate;
  private LocalDate cancellationDate;
  private LocalDate transactionDate;
  private LocalDate rvDateOriginal;
  private LocalDate adjustedRvDate;
  private LocalDate negotiationDate;
  private BigDecimal rvValueOriginal;
  private BigDecimal adjustmentValue;
  private BigDecimal cancellationValue;
  private BigDecimal newInstallmentValue;
  private BigDecimal originalValueChangedInstallment;
  private Long negotiationContractNumber;
  private String partnerCnpj;

  @ManyToOne(fetch = FetchType.LAZY)
  private FlagEntity flagRvOrigin;

  @ManyToOne(fetch = FetchType.LAZY)
  private FlagEntity flagRvAdjusted;

  @ManyToOne(fetch = FetchType.LAZY)
  private AcquirerEntity acquirer;

  @ManyToOne(fetch = FetchType.LAZY)
  private CompanyEntity company;

  @ManyToOne(fetch = FetchType.LAZY)
  private EstablishmentEntity establishment;

  @ManyToOne(fetch = FetchType.LAZY)
  private ProcessedFileEntity processedFile;
}
