package com.cardsync.domain.model;

import com.cardsync.domain.model.enums.ModalityPaymentBankEnum;
import com.cardsync.domain.model.enums.ReleaseCategoryEnum;
import com.cardsync.domain.model.enums.StatusPaymentBankEnum;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "cs_releases_bank")
public class ReleasesBankEntity extends AuditableEntityBase {

  private Integer lineNumber;
  private Integer serviceLot;
  private Integer numberParcels;
  private Integer releaseCategory;
  private Integer sequentialNumber;
  private Integer numberCreditOrders;
  private Integer historicalCodeBank;
  private Integer modalityPaymentBank;
  private Integer releaseCategoryCode;
  private Integer reconciliationStatus;
  private Integer typeComplementRelease;
  private Integer numberReconciliations;
  private Integer companyRegistrationType;

  private String recordType;
  private String segmentCode;
  private String releaseType;
  private String natureRelease;
  private String bankAgreementCode;
  private String complementRelease;
  private String documentComplementNumber;
  private String descriptionHistoricalBank;
  private String cpmfExemptionIdentification;

  private BigDecimal releaseValue;

  private LocalDate releaseDate;
  private LocalDate accountingDate;

  @ManyToOne(fetch = FetchType.LAZY)
  private FlagEntity flag;

  @ManyToOne(fetch = FetchType.LAZY)
  private BankEntity bank;

  @ManyToOne(fetch = FetchType.LAZY)
  private AcquirerEntity acquirer;

  @ManyToOne(fetch = FetchType.LAZY)
  private CompanyEntity company;

  @ManyToOne(fetch = FetchType.LAZY)
  private EstablishmentEntity establishment;

  @ManyToOne(fetch = FetchType.LAZY)
  private ProcessedFileEntity processedFile;

  @ManyToOne(fetch = FetchType.LAZY)
  private BankingDomicileEntity bankingDomicile;

  public ModalityPaymentBankEnum getModalityPaymentBank() {
    return ModalityPaymentBankEnum.fromCode(modalityPaymentBank);
  }

  public void setModalityPaymentBank(ModalityPaymentBankEnum value) {
    this.modalityPaymentBank = Optional.ofNullable(value).orElse(ModalityPaymentBankEnum.NULL).getCode();
  }

  public ReleaseCategoryEnum getReleaseCategory() {
    return ReleaseCategoryEnum.fromCode(releaseCategory);
  }

  public void setReleaseCategory(ReleaseCategoryEnum value) {
    this.releaseCategory = Optional.ofNullable(value).orElse(ReleaseCategoryEnum.NULL).getCode();
  }

  public StatusPaymentBankEnum getReconciliationStatus() {
    return StatusPaymentBankEnum.fromCode(reconciliationStatus);
  }

  public void setReconciliationStatus(StatusPaymentBankEnum value) {
    this.reconciliationStatus = Optional.ofNullable(value).orElse(StatusPaymentBankEnum.NULL).getCode();
  }
}
