package com.cardsync.domain.model;

import com.cardsync.domain.model.enums.CaptureEnum;
import com.cardsync.domain.model.enums.ContractAuditStatusEnum;
import com.cardsync.domain.model.enums.ModalityEnum;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
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
@Table(name = "cs_contract_audit")
public class ContractAuditEntity extends AuditableEntityBase {

  private Integer status;
  private Integer capture;
  private Integer modality;

  private Long nsu;
  private String authorization;

  private BigDecimal grossValue;
  private BigDecimal liquidValue;
  private BigDecimal rateAcquirer;
  private BigDecimal rateContract;
  private BigDecimal discountValue;
  private BigDecimal differenceValue;
  private BigDecimal expectedDiscountValue;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "flag_id")
  private FlagEntity flag;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "acquirer_id")
  private AcquirerEntity acquirer;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "contract_id")
  private ContractEntity contract;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "company_id")
  private CompanyEntity company;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "establishment_id")
  private EstablishmentEntity establishment;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "transaction_acq_id")
  private TransactionAcqEntity transactionAcq;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "transaction_erp_id")
  private TransactionErpEntity transactionErp;

  public ContractAuditStatusEnum getStatus() {
    return ContractAuditStatusEnum.fromCode(status);
  }

  public void setStatus(ContractAuditStatusEnum status) {
    this.status = (status != null ? status : ContractAuditStatusEnum.NULL).getCode();
  }

  public ModalityEnum getModality() {
    return ModalityEnum.fromCode(modality);
  }

  public void setModality(ModalityEnum modality) {
    this.modality = (modality != null ? modality : ModalityEnum.NULL).getCode();
  }

  public void setModalityCode(Integer modality) {
    this.modality = modality;
  }

  public CaptureEnum getCapture() {
    if (capture == null) return null;
    for (CaptureEnum value : CaptureEnum.values()) {
      if (value.getCode() == capture) return value;
    }
    return CaptureEnum.NULL;
  }

  public void setCapture(CaptureEnum capture) {
    this.capture = (capture != null ? capture : CaptureEnum.NULL).getCode();
  }

  public void setCaptureCode(Integer capture) {
    this.capture = capture;
  }
}
