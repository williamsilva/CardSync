package com.cardsync.domain.model;

import com.cardsync.domain.model.enums.ErpCommercialStatusEnum;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "cs_transaction_erp")
public class TransactionErpEntity extends AuditableEntityBase {

  private Long nsu;

  private Integer capture;
  private Integer modality;

  @Column(name = "line_number")
  private Integer lineNumber;
  private Integer installment;
  @Column(name = "transaction_status")
  private Integer transactionStatus;
  @Column(name = "reason_exclusion_status")
  private Integer reasonExclusionStatus;
  @Column(name = "transaction_status_reason")
  private Integer transactionStatusReason;

  private String tid;
  private String origin;
  @Column(name = "three_ds")
  private String threeDs;
  private String machine;
  @Column(name = "card_name")
  private String cardName;
  @Column(name = "anti_fraud")
  private String antiFraud;
  @Column(name = "card_number")
  private String cardNumber;
  @Column(name = "transaction_type")
  private String transaction;
  private String observations;
  private String authorization;
  @Column(name = "source_company_cnpj", length = 32)
  private String sourceCompanyCnpj;
  @Column(name = "source_company_name", length = 255)
  private String sourceCompanyName;
  @Column(name = "source_establishment_pv_number")
  private Integer sourceEstablishmentPvNumber;
  @Column(name = "source_establishment_name", length = 255)
  private String sourceEstablishmentName;
  @Column(name = "installment_type")
  private String installmentType;

  @Column(name = "gross_value")
  private BigDecimal grossValue;
  @Column(name = "liquid_value")
  private BigDecimal liquidValue;
  @Column(name = "discount_value")
  private BigDecimal discountValue;
  @Column(name = "contracted_fee")
  private BigDecimal contractedFee;

  @Enumerated(EnumType.STRING)
  @Column(name = "commercial_status", length = 40)
  private ErpCommercialStatusEnum commercialStatus = ErpCommercialStatusEnum.OK;

  @Column(name = "commercial_status_message", length = 500)
  private String commercialStatusMessage;

  @Column(name = "canceled_date")
  private LocalDate canceledDate;
  @Column(name = "sale_date")
  private OffsetDateTime saleDate;
  @Column(name = "deleted_date")
  private OffsetDateTime deletedDate;
  @Column(name = "sale_reconciliation_date")
  private OffsetDateTime saleReconciliationDate;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "flag_id")
  private FlagEntity flag;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "acquirer_id")
  private AcquirerEntity acquirer;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "company_id")
  private CompanyEntity company;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "adjustment_id")
  private AdjustmentEntity adjustment;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "processed_file_id")
  private ProcessedFileEntity processedFile;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "establishment_id")
  private EstablishmentEntity establishment;

  @BatchSize(size = 100)
  @OrderBy("installment ASC")
  @OneToMany(mappedBy = "transaction", cascade = CascadeType.ALL, orphanRemoval = true)
  private Set<InstallmentErpEntity> installments = new LinkedHashSet<>();

  public void addInstallment(InstallmentErpEntity installment) {
    installments.add(installment);
    installment.setTransaction(this);
  }
}
