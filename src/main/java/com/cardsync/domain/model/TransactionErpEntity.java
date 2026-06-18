package com.cardsync.domain.model;

import com.cardsync.domain.model.enums.ErpCommercialStatusEnum;
import com.cardsync.domain.model.enums.FeeReconciliationStatusEnum;
import com.cardsync.domain.model.enums.StatusTransactionEnum;
import com.cardsync.domain.model.enums.StatusTransactionReasonEnum;
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
import java.util.Optional;
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
  private Integer lineNumber;
  private Integer installment;
  private Integer statusTransaction;
  private Integer reasonExclusionStatus;
  private Integer statusTransactionReason;
  private Integer sourceEstablishmentPvNumber;
  private Integer feeReconciliationStatus = FeeReconciliationStatusEnum.PENDING.getCode();

  private Boolean missingContractAtSale = Boolean.FALSE;

  private String tid;
  private String origin;
  private String machine;
  private String cardName;
  private String cardNumber;
  private String observations;
  private String authorization;
  private String transactionType;
  private String installmentType;
  private String sourceCompanyCnpj;
  private String sourceCompanyName;
  private String commercialStatusMessage;

  private BigDecimal grossValue;
  private BigDecimal liquidValue;
  private BigDecimal discountValue;
  private BigDecimal contractedFee;

  @Enumerated(EnumType.STRING)
  @Column(name = "commercial_status", length = 40)
  private ErpCommercialStatusEnum commercialStatus = ErpCommercialStatusEnum.OK;

  private LocalDate canceledDate;
  private OffsetDateTime saleDate;
  private OffsetDateTime deletedDate;
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
  @JoinColumn(name = "banking_domicile_id")
  private BankingDomicileEntity bankingDomicile;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "adjustment_id")
  private AdjustmentEntity adjustment;

  /**
   * Venda da adquirente vinculada na conciliação ERP x Adquirente.

   * Esse vínculo evita redescobrir o match em consultas posteriores e permite
   * reaproveitar rapidamente o contexto da adquirente, como ajuste, domicílio
   * bancário, empresa, estabelecimento, bandeira e adquirente.
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "transaction_acq_id")
  private TransactionAcqEntity transactionAcq;

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

  public StatusTransactionEnum getStatusTransaction() {
    return StatusTransactionEnum.fromCode(statusTransaction);
  }

  public void setStatusTransaction(StatusTransactionEnum statusTransaction) {
    this.statusTransaction = Optional.ofNullable(statusTransaction).orElse(StatusTransactionEnum.NULL).getCode();
  }

  public StatusTransactionReasonEnum getStatusTransactionReason() {
    return StatusTransactionReasonEnum.fromCode(statusTransactionReason);
  }

  public void setStatusTransactionReason(StatusTransactionReasonEnum statusTransactionReason) {
    this.statusTransactionReason = StatusTransactionReasonEnum.toCode(statusTransactionReason);
  }

  public FeeReconciliationStatusEnum getFeeReconciliationStatus() {
    return FeeReconciliationStatusEnum.fromCode(feeReconciliationStatus);
  }

  public void setFeeReconciliationStatus(FeeReconciliationStatusEnum feeReconciliationStatus) {
    this.feeReconciliationStatus = FeeReconciliationStatusEnum.toCode(feeReconciliationStatus);
  }

  public void addInstallment(InstallmentErpEntity installment) {
    installments.add(installment);
    installment.setTransaction(this);
  }
}
